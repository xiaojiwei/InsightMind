package com.graphinsight.indicator.service.impl;

import com.alibaba.fastjson.JSONObject;
import com.baomidou.dynamic.datasource.toolkit.DynamicDataSourceContextHolder;
import com.graphinsight.indicator.auto.entity.OperateGrantConfig;
import com.graphinsight.indicator.auto.service.IOperateGrantConfigService;
import com.graphinsight.indicator.dao.DimContextRelationDao;
import com.graphinsight.indicator.enums.*;
import com.graphinsight.indicator.model.*;
import com.graphinsight.indicator.service.*;
import com.graphinsight.indicator.util.CloneUtils;
import com.graphinsight.indicator.util.StringUtil;
import com.graphinsight.indicator.util.UserThreadLocalUtil;
import lombok.extern.slf4j.Slf4j;
import org.owasp.esapi.ESAPI;
import org.owasp.esapi.codecs.MySQLCodec;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Lazy;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;

import javax.annotation.Resource;
import java.util.*;

@Slf4j
@Transactional
@Service
public class DimensionQueryServiceImpl implements DimensionQueryService {

    @Autowired
    @Qualifier("secondJdbcTemplate")
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private IndicatorService indicatorService;

    @Autowired
    private QueryPlanService queryPlanService;

    @Resource
    protected RedisCacheService redisCacheService;

    @Lazy
    @Autowired
    private SyncTaskService syncTaskService;

    @Autowired
    private SqlCheckService sqlCheckService;

    @Autowired
    private ChartQueryService chartQueryService;

    @Autowired
    private DimContextRelationDao dimContextRelationDao;

    @Autowired
    private IOperateGrantConfigService iOperateGrantConfigService;

    @Override
    public PageData execQueryDimensionValues(DimensionQueryParam dimQueryParam, Boolean isSyncUpdate) {

        /**
         * 缓存策略
         */
        CacheStrategy cacheStrategy = dimQueryParam.getCacheStrategy();
        cacheStrategy = CacheStrategy.OVERWRITE;

        String userName = dimQueryParam.getUsername();
        userName = BuildSqlServiceImpl.formatSqlValue(userName);

        if (StringUtil.isEmpty(userName)) {
            userName = UserThreadLocalUtil.getUserName();
            dimQueryParam.setUsername(userName);
        }

        if (null == cacheStrategy) {
            //容错，默认从缓存中查询结果
            cacheStrategy = CacheStrategy.QUERY_UPDATE;
        }

        PageData pageData = null;
        String md5Key = null;
        if (StringUtil.isNotEmpty(dimQueryParam.getMd5Key())) {
            md5Key = dimQueryParam.getMd5Key();
        } else {
            md5Key = this.buildMd5Key(dimQueryParam);
        }

        if (isSyncUpdate) {
            //系统内部自己的异步调用方法，只更新缓存,无返回。
            this.query(md5Key, dimQueryParam);
            return null;
        }

        if (CacheStrategy.OVERWRITE.equals(cacheStrategy)) {
            pageData = this.query(md5Key, dimQueryParam);
            queryPlanService.supQueryPlan(md5Key, pageData);
        } else if (CacheStrategy.DELETE.equals(cacheStrategy)) {
            this.queryPlanService.delete(md5Key);
        } else {

            //to do 直接先从缓存中获取数据
            //从缓存获取数据
            pageData = this.queryPlanService.getData(md5Key);
            boolean isQuery = false;
            if (null == pageData || pageData.getCellList().size() == 0) {
                //缓存中无结果则直接查询
                pageData = this.query(md5Key, dimQueryParam);
                isQuery = true;
            }
            queryPlanService.supQueryPlan(md5Key, pageData);

            //需要异步更新数据
            if (CacheStrategy.QUERY_UPDATE.equals(cacheStrategy) && !isQuery) {
                dimQueryParam.setMd5Key(md5Key);
                userName = UserThreadLocalUtil.getUserName();
                dimQueryParam.setUsername(userName);
                syncTaskService.syncUpdate(dimQueryParam);
            }

        }

        return pageData;
    }

    private PageData query(String md5Key, DimensionQueryParam dimQueryParam) {

        long begin = System.currentTimeMillis();
        boolean isAuth = dimQueryParam.isAuth();
        if (null == dimQueryParam.getUsername()) {
            dimQueryParam.setUsername(UserThreadLocalUtil.getUserName());
        }
        if (isAuth) {
            dimQueryParam = this.applyAuthFilter(dimQueryParam);
        } else {
            dimQueryParam.getFilterList().clear();
        }

        PageData pageData = this.execQueryDimensionValues(dimQueryParam);

        /* 总耗时 */
        Long cost = System.currentTimeMillis() - begin;
        queryPlanService.addCache(md5Key, pageData, cost);

        return pageData;

    }

    private DimensionQueryParam applyAuthFilter(DimensionQueryParam dimQueryParam) {

        //当前登录人在空间下配置的所有维度、指标权限
        String username = dimQueryParam.getUsername();
        if (null == username || "anonymous".equals(username)) {
            username = UserThreadLocalUtil.getUserName();
        }
        Long spaceId = dimQueryParam.getSpaceId();
        Set<AuthElement> authElementSet = this.chartQueryService.getAuthElementSet(spaceId, username);

        for (AuthElement authElement : authElementSet) {

            String dimCode = dimQueryParam.getCode();
            Filter filter = authElement.getFilter();
            if (dimCode.equalsIgnoreCase(authElement.getCode()) && null != filter) {

                if (authElement.getAuthElementMeasureSet().size() > 0 || AuthElementType.MEASURE.equals(authElement.getAuthElementType())) {
                    continue;
                }

                List<Operator> operatorList = filter.getOperatorList();

                //进行维度上下文的授权值处理
                AuthFilterParamType authFilterParamType = filter.getAuthFilterParamType();
                if (AuthFilterParamType.CONTEXT.equals(authFilterParamType)) {
                    for (Operator operator : operatorList) {

                        operator.setSqlOprType(SqlOprType.IN);
                        List<String> dataList = operator.getDataList();

                        if (!CollectionUtils.isEmpty(dataList)) {

                            String data = dataList.get(0);
                            List<String> contextDataList = this.chartQueryService.applyAuthContextDataList(data, username);
                            operator.setDataList(contextDataList);

                        }

                    }

                }

                dimQueryParam.getFilterList().add(authElement.getFilter());
            }

        }

        return dimQueryParam;

    }

    private DimensionQueryParam initProp(DimensionQueryParam queryParam) {

        queryParam.setCacheStrategy(null);
        queryParam.setTraceId(null);
        queryParam.setUpdateDate(null);
        queryParam.setCreateDate(null);

        List<Filter> filterList = queryParam.getFilterList();
        List<Filter> newFilterList = new ArrayList<>();
        if (!CollectionUtils.isEmpty(filterList)) {
            for (Filter filter : filterList) {
                filter.setUpdateDate(null);
                filter.setCreateDate(null);
                List<Operator> operatorList = filter.getOperatorList();
                if (!CollectionUtils.isEmpty(operatorList)) {
                    for (Operator operator : operatorList) {
                        List<String> dataList = operator.getDataList();
                        operator.setCreateDate(null);
                        operator.setUpdateDate(null);
                        if (!CollectionUtils.isEmpty(dataList)) {
                            newFilterList.add(filter);
                        }
                    }
                }
            }
        }

        if (newFilterList.size() > 0) {
            queryParam.setFilterList(newFilterList);
        } else {
            queryParam.setFilterList(null);
        }

        return queryParam;

    }

    private String buildMd5Key(DimensionQueryParam queryParam) {

        DimensionQueryParam keyQueryParam = CloneUtils.clone(queryParam);
        keyQueryParam = this.initProp(keyQueryParam);

        DataSource dataSource = new DataSource();
        dataSource.setSpaceId(queryParam.getSpaceId());
        String authKey = chartQueryService.buildAuthCacheKeyInfo(dataSource);
        keyQueryParam.setCode(authKey.toString());

        JSONObject keyJson = (JSONObject)JSONObject.toJSON(keyQueryParam);
        String keyStr = keyJson.toString();
        String md5Key = "DQP_" + StringUtil.encrypt(keyStr);

        redisCacheService.put("DS_" + md5Key, queryParam);

        return md5Key;

    }

    @Override
    public Integer getDimCount(String dimCode) {

        DimensionQueryParam dimQueryParam = new DimensionQueryParam();
        dimQueryParam.setCode(dimCode);
        dimQueryParam.setCacheStrategy(CacheStrategy.QUERY_UPDATE);
        PageData pageData = this.execQueryDimensionValues(dimQueryParam);
        final int count = pageData.getCellList().size();
        return count;

    }

    /**
     * 1.根据筛选维度code定位具体维度
     * 2.根据维度信息、联动维度信息明确数据来源
     *    2.1 维度表
     *    2.2 无维表，指标平台自己维护
     *    2.3 退化维来源事实表
     * 3.根据筛选条件进行筛选
     *    3.1 维度本身like筛选
     *    3.2 关联维度的联动筛选条件
     *       3.2.1 获取关联维度
     *       3.2.2 获取关联维度筛选列信息
     * @param dimQueryParam
     * @return
     */
    @Override
    public PageData execQueryDimensionValues(DimensionQueryParam dimQueryParam) {

        String dimCode = dimQueryParam.getCode();
        //目标维度
        Dimension dim = indicatorService.getDimensionTableInfo(dimCode);
        this.sqlCheckService.checkDimension(dim, new ArrayList<>(), dimCode,false);

        DimType dimType = dim.getDimType();
        String executeSql = "";
        Table dimTable = null;

        //是否需要sql查询数据，衍生维度不需要从db中查询。
        boolean sqlQuery = true;
        List<Map<String, Object>> list = null;

        log.info("DimType:{} dimID:{} dimCode:{}", dimType, dim.getId(), dimCode);

        if (DimType.STD_WITH_TABLE.equals(dimType)) {
            list = new LinkedList<>();
            //标准维有维表
            List<GroupColumn> groupColumnList = dim.getGroupColumnList();
            if (!CollectionUtils.isEmpty(groupColumnList)) {
                //衍生维度处理
                sqlQuery = false;
                dimTable = new Table();
                //维度表信息
                dimTable.setDimPrimaryKey("v_key");
                dimTable.setDimColumn("v_value");
                for (GroupColumn groupColumn : groupColumnList) {

                    Map<String, Object> rowMap = new HashMap<>();

                    rowMap.put(dimTable.getDimPrimaryKey(), groupColumn.getName());
                    rowMap.put(dimTable.getDimColumn(), groupColumn.getName());

                    list.add(rowMap);

                }

            } else {
                //非衍生维度
                dimTable = this.findDimensionTable(dim);
                executeSql = this.buildSelectSql(dimQueryParam, dim, dimTable);
            }

        } else if (DimType.STD_WITHOUT_TABLE.equals(dimType)) {
            //标准维无维表
            List<Table> dimTableList = dim.getDimTableList();
            if (!CollectionUtils.isEmpty(dimTableList)) {

                dimTable = dim.getDimTableList().get(0);
                log.info("dimTable:{} dimID:{}", dimTable, dim.getId());

                executeSql = this.buildSelectSql(dimQueryParam, dim, dimTable);
                log.info("executeSql:{} dimID:{}", executeSql, dim.getId());

            }

        }  else if (DimType.DEGENERATE_DIM.equals(dimType)) {

            dimTable = new Table();
            //维度表信息
            dimTable.setDimPrimaryKey("v_key");
            dimTable.setDimColumn("v_key");

            CacheStrategy cacheStrategy = dimQueryParam.getCacheStrategy();

            if (!CacheStrategy.OVERWRITE.equals(cacheStrategy)) {
                // cache 读取
//                list = this.queryByCacheTable(dimQueryParam, dim);
            }

            if (null == list) {
                //查询结果为null，从cache中查询师表，需要构建退化维值sql查询。
                executeSql = this.buildSelectSqlByDegenerte(dimQueryParam, dim);
            } else {
                //查询成功
                sqlQuery = false;
            }

        }

        //判断是否从db中查询
        if (sqlQuery) {
//            DynamicDataSourceContextHolder.push(JdbcDataSourceType.DORIS.getDesc());
            if (StringUtil.isEmpty(executeSql)) {
                list = new LinkedList<>();
            } else {
                List<Table> factTableList = dim.getFactTableList();
                if (factTableList.size() == 0 && !DimType.STD_WITHOUT_TABLE.equals(dimType) && !DimType.STD_WITH_TABLE.equals(dimType)) {
                    list = new ArrayList<>();
                } else {
                    list = jdbcTemplate.queryForList(executeSql);
                }

            }

        }

        Integer pageSize = dimQueryParam.getPageSize();
        Integer pageNo = dimQueryParam.getPageNo();

        PageInfo pageInfo = new PageInfo(pageSize);
        pageInfo.setTotalRows(list.size());
        pageInfo.calc();
        pageInfo.calcRange(pageNo);

        PageData pageData = this.buildPageData(list, dim, dimTable, pageInfo);
        //上下文环境中如果存在维度的配置，此处添加。
        boolean isGrant = dimQueryParam.isGrade();
        if (isGrant) {
            pageData = this.applyContext(pageData, dim);
        }

        pageData.setReviewSql(executeSql);
        pageData.setPageInfo(pageInfo);
        //维度类型
        pageData.setDimType(dimType);

        return pageData;

    }

    private PageData applyContext(PageData pageData, Dimension dim) {

        LinkedList<List<Cell>> cellList = (LinkedList) pageData.getCellList();

        List<DimContextRelation> dimContextRelationList = dimContextRelationDao.findAll();
        for (DimContextRelation dimContextRelation : dimContextRelationList) {

            List<Cell> rowCellList = new LinkedList<Cell>();
            Cell dimCell = new Cell();
            Long grantId = dimContextRelation.getGrantConfigId();
            OperateGrantConfig operateGrantConfig = iOperateGrantConfigService.getById(grantId);

            String id = "#" + String.valueOf(operateGrantConfig.getId());
            String data = "#" + operateGrantConfig.getName();
            dimCell.setId(id);
            dimCell.setData(data);
            dimCell.setName(dim.getName());
            dimCell.setType(CellType.DIMENSION);
            dimCell.setCode(dim.getCode());
            dimCell.setDimType(dim.getDimType());
            rowCellList.add(dimCell);

            cellList.addFirst(rowCellList);

        }

        return pageData;

    }

    /**
     * 从缓存中查询数据
     * @return
     */
    private List<Map<String, Object>> queryByCacheTable(DimensionQueryParam dimQueryParam, Dimension dim) {

        String dimCode = dim.getCode();
        String mvTableName = "MV_DIM_" + dimCode;

        if (!dimQueryParam.getCacheTable()) {
            //退化维预热时，会设置不允许从缓存中查询，必须从表中获取，此处直接返回null时，直接从sql中查询。
            return null;
        }

        //all data
        List<Map<String, Object>> mapList = this.redisCacheService.get(mvTableName, List.class);
        if (null == mapList) {
            //如果缓存中不存在数据，直接返回为空，返回null时，会直接从sql中查询。
            return null;
        }

        List<Map<String, Object>> dataList = new LinkedList<>();

        //筛选项
        List<Filter> filterList = dimQueryParam.getFilterList();
        if (!CollectionUtils.isEmpty(filterList)) {

            for (Filter filter : filterList) {

                String filterCode = filter.getCode();
                if (!filterCode.equalsIgnoreCase(dimCode)) {
                    //容错 当筛选项与退化维code不一致时，直接跳过。
                    continue;
                }
                //筛选项
                List<Operator> operatorList = filter.getOperatorList();
                if (!CollectionUtils.isEmpty(operatorList)) {
                    for (Operator operator : operatorList) {

                        SqlOprType sqlOprType = operator.getSqlOprType();
                        List<String> valueList = operator.getDataList();

                        if (!CollectionUtils.isEmpty(valueList) && dim.getCode().equalsIgnoreCase(filterCode)) {
                            if (SqlOprType.LIKE.equals(sqlOprType)) {
                                //本身的操作
                                String textValue = valueList.get(0);//文本框搜索的值

                                for (Map<String, Object> stringObjectMap : mapList) {

                                    String key = String.valueOf(stringObjectMap.get("v_key"));
                                    if (null != key && key.indexOf(textValue) >= 0) {
                                        dataList.add(stringObjectMap);
                                    }

                                }

                            } else if (SqlOprType.IN.equals(sqlOprType)) {
                                //相关操作
                                for (Map<String, Object> stringObjectMap : mapList) {

                                    String key = String.valueOf(stringObjectMap.get("v_key"));
                                    //valueList id 集合
                                    if (valueList.contains(key)) {
                                        dataList.add(stringObjectMap);
                                    }

                                }
                            }
                        }
                    }
                }
            }
        } else {
            for (Map<String, Object> stringObjectMap : mapList) {
                dataList.add(stringObjectMap);
            }
        }

        return dataList;

    }

    private String buildMd5Key(Dimension dim) {

        Dimension keyDim = CloneUtils.clone(dim);
        JSONObject keyJson = (JSONObject)JSONObject.toJSON(keyDim);
        String keyStr = keyJson.toString();
        String md5Key = "DIM_" + StringUtil.encrypt(keyStr);

        redisCacheService.put("DS_" + md5Key, dim);

        return md5Key;

    }

    @Override
    public void buildDegenerateTable(Dimension dim) {

        Long begin = System.currentTimeMillis();
        DimensionQueryParam nullQueryParam = new DimensionQueryParam();

        String executeSql = this.buildSelectSqlByDegenerte(nullQueryParam, dim);
        List<Map<String, Object>> rowList = jdbcTemplate.queryForList(executeSql);

        Long cost = System.currentTimeMillis() - begin;

        PageData pageData = new PageData();
        pageData.setReviewSql(executeSql);
        pageData.setRowList(rowList);

        this.queryPlanService.addCache(dim.getCode(), pageData, cost);

    }

    private PageData buildPageData(List<Map<String, Object>> list, Dimension dim, Table dimTable, PageInfo pageInfo) {

        PageData pageData = new PageData();

        String primaryKey = dimTable.getDimPrimaryKey();
        String column = dimTable.getDimColumn();

        LinkedList<List<Cell>> cellList = new LinkedList<>();
        List<Map<String, Object>> pageList = new LinkedList<>();

        Integer pageStartRow = pageInfo.getPageStartRow();
        Integer pageEndRow = pageInfo.getPageEndRow();

        if (!CollectionUtils.isEmpty(list)) {

            for (int i = 0; i < list.size(); i++) {

                if (i >= pageStartRow && i < pageEndRow) {
                    Map<String, Object> strObjMap = list.get(i);

                    pageList.add(strObjMap);

                    List<Cell> cells = new LinkedList<Cell>();
                    Cell dimCell = new Cell();

                    String id = String.valueOf(strObjMap.get(primaryKey));
                    String data = null;
                    if (primaryKey.equalsIgnoreCase(column)) {
                        data = id;
                    } else {
                        data = String.valueOf(strObjMap.get(column));
                    }


                    dimCell.setId(id);
                    dimCell.setData(data);
                    dimCell.setName(dim.getName());
                    dimCell.setType(CellType.DIMENSION);
                    dimCell.setCode(dim.getCode());
                    dimCell.setDimType(dim.getDimType());
                    cells.add(dimCell);

                    cellList.add(cells);
                }

            }

        }

        pageData.setRowList(pageList);
        pageData.setCellList(cellList);


        return pageData;
    }

    private String buildSelectSqlByDegenerte(DimensionQueryParam dimQueryParam, Dimension dim) {

        //维度表信息
        List<Table> factTableList = dim.getFactTableList();
        List<String> sqlList = new ArrayList<>();

        for (Table factTable : factTableList) {

            String masterPrimaryKey = factTable.getMasterPrimaryKey();
            String table = factTable.getTableName();
            String schema = factTable.getSchemaName();

            StringBuilder selDimSqlBuffer = new StringBuilder();

            selDimSqlBuffer.append("select distinct ")
                    .append(masterPrimaryKey)
                    .append(" as v_key ")
                    .append(" from ")
                    .append(schema)
                    .append(".")
                    .append(table);

            List<Filter> filterList = dimQueryParam.getFilterList();

            boolean hasDt = factTable.getHasColumnDT();
            if (hasDt) {
                selDimSqlBuffer.append(" where dt=DATE_FORMAT(DATE_SUB(NOW(), INTERVAL 1 DAY), '%Y-%m-%d') ");
            }

            Filter authDimFilter = this.getAuthDimFilter(dimQueryParam);
            if (null != authDimFilter) {
                filterList.add(authDimFilter);
            }

            if (!CollectionUtils.isEmpty(filterList)) {

                if (!hasDt) {
                    selDimSqlBuffer.append(" where 1=1 ");
                }

                boolean isFirstLogical = true;
                for (Filter filter : filterList) {

                    String filterCode = filter.getCode();

                    //筛选项
                    List<Operator> operatorList = filter.getOperatorList();
                    if (!CollectionUtils.isEmpty(operatorList)) {
                        for (Operator operator : operatorList) {

                            SqlLogicalType sqlLogicalType = operator.getSqlLogicalType();
                            String logicalString = SqlLogicalType.OR.equals(sqlLogicalType) ? " or " : " and ";
                            if (isFirstLogical) {
                                logicalString = " and ";
                                isFirstLogical = false;
                            }

                            SqlOprType sqlOprType = operator.getSqlOprType();
                            List<String> valueList = operator.getDataList();

                            if (!CollectionUtils.isEmpty(valueList) && dim.getCode().equalsIgnoreCase(filterCode)) {
                                if (SqlOprType.LIKE.equals(sqlOprType)) {
                                    //本身的操作
                                    String textValue = valueList.get(0);//文本框搜索的值
                                    selDimSqlBuffer.append(logicalString).append("CONCAT(" + masterPrimaryKey + ", '')").append(" like '%").append(textValue).append("%'");

                                } else if (SqlOprType.IN.equals(sqlOprType)) {
                                    List<String> vs = new ArrayList<>();
                                    for (String value : valueList) {
                                        vs.add("'" + value + "'");
                                    }
                                    //相关操作
                                    String ids = StringUtil.join(vs, ",");
                                    selDimSqlBuffer.append(logicalString).append("CONCAT(" + masterPrimaryKey+ ", '')").append(" in (").append(ids).append(")");
                                }
                            }
                        }
                    }
                }
            }

//            selDimSqlBuffer.append(" order by v_key desc");
//            selDimSqlBuffer.append(" limit ").append(pageSize);
            sqlList.add(selDimSqlBuffer.toString());

        }

        StringBuilder selDimSqlBuffer = new StringBuilder();
        for (int i = 0; i < sqlList.size(); i++) {

            String sql = sqlList.get(i);

            if (i > 0) {
                selDimSqlBuffer.append(" union ");
            }

            selDimSqlBuffer.append(sql);

        }

        String allDataSql = selDimSqlBuffer.toString();
        Integer pageSize = dimQueryParam.getPageSize();
        Integer pageNo = dimQueryParam.getPageNo();
        Integer startRow = 0;
        if (null == pageNo || pageNo.equals(1)) {
            startRow = 0;
        } else {
            startRow = pageNo * (pageNo - 1);
        }
        String filterSql = "select t.v_key from (" +allDataSql + ") as t order by t.v_key desc limit " + startRow + "," + pageSize;
//        String filterSql = "select t.v_key from (" + allDataSql + ") as t order by t.v_key desc";
        return filterSql;

    }

    @Autowired
    private SpaceService spaceService;


    private Filter getAuthDimFilter(DimensionQueryParam dimQueryParam) {

        Long spaceId = dimQueryParam.getSpaceId();
        String dimCode = dimQueryParam.getCode();

        Filter authFilter = null;

        //此处增加权限筛选所拥有的过滤项
        Set<AuthElement> authElementSet = spaceService.getAuthElementBySpaceId(spaceId, dimQueryParam.getUsername());
        for (AuthElement authElement : authElementSet) {

            if (AuthElementType.MEASURE.equals(authElement.getAuthElementType()) || authElement.getAuthElementMeasureSet().size() > 0) {
                continue;
            }

            String authCode = authElement.getCode();
            if (dimCode.equalsIgnoreCase(authCode)) {
                authFilter = authElement.getFilter();
                authFilter = ChartQueryServiceImpl.deelpCopy(authFilter);

                List<Operator> operatorList = authFilter.getOperatorList();
                //进行维度上下文的授权值处理
                for (Operator operator : operatorList) {

                    List<String> dataList = operator.getDataList();
                    if (!CollectionUtils.isEmpty(dataList)) {

                        List<String> delList = new ArrayList<>();
                        List<String> addList = new ArrayList<>();
                        for (String data : dataList) {

                            if (data.indexOf("#") == 0) {

                                delList.add(data);

                                String dataId = data.replaceFirst("#", "");
                                List<String> contextDataList = chartQueryService.applyAuthContextDataList(dataId, dimQueryParam.getUsername());
                                if (!CollectionUtils.isEmpty(contextDataList)) {
                                    addList.addAll(contextDataList);
                                } else {
                                    return null;
                                }

                            }

                        }

                        dataList.removeAll(delList);
                        dataList.addAll(addList);

                    }

                }

            }


        }

        return authFilter;

    }

    private List<Filter> removeNullFilter(List<Filter> filterList) {

        List<Filter> delFilter = new ArrayList<>();
        for (Filter filter : filterList) {
            List<Operator> operatorList = filter.getOperatorList();
            if (!CollectionUtils.isEmpty(operatorList)) {
                for (Operator operator : operatorList) {

                    SqlOprType sqlOprType = operator.getSqlOprType();
                    List<String> valueList = operator.getDataList();
                    if (!CollectionUtils.isEmpty(valueList)) {
                        if (SqlOprType.LIKE.equals(sqlOprType)) {
                            //本身的操作
                            String textValue = valueList.get(0);//文本框搜索的值
                            if (StringUtil.isEmpty(textValue)) {
                                delFilter.add(filter);
                            }
                        }
                    }
                }
            }
        }

        filterList.removeAll(delFilter);

        return filterList;

    }

    private List<Filter> hasAuthFilter(List<Filter> filterList) {

        List<Filter> delFilterList = new ArrayList<>();

        if (!CollectionUtils.isEmpty(filterList)) {
            for (Filter filter : filterList) {

                if (null != filter) {
                    List<Operator> operatorList = filter.getOperatorList();
                    if (!CollectionUtils.isEmpty(operatorList)) {
                        for (Operator operator : operatorList) {

                            if (null != operator) {

                                if (CollectionUtils.isEmpty(operator.getDataList())) {
                                    delFilterList.add(filter);
                                }

                            }

                        }
                    } else {
                        delFilterList.add(filter);
                    }
                }

            }
        }

        filterList.removeAll(delFilterList);

        return filterList;

    }

    private String buildSelectSql(DimensionQueryParam dimQueryParam, Dimension dim, Table dimTable) {

        Integer pageSize = dimQueryParam.getPageSize();
        String dimCode = dimQueryParam.getCode();

        dimCode = BuildSqlServiceImpl.formatSqlValue(dimCode);
        //维度表信息
        String key = dimTable.getDimPrimaryKey();
        String column = dimTable.getDimColumn();
        String table = dimTable.getTableName();
        String schema = dimTable.getSchemaName();

        StringBuilder selDimSqlBuffer = new StringBuilder();

        if (key.equalsIgnoreCase(column)) {
            selDimSqlBuffer.append("select distinct ")
                    .append(key)
                    .append(" from ")
                    .append(schema)
                    .append(".")
                    .append(table);
        } else {
            selDimSqlBuffer.append("select distinct ")
                    .append(key)
                    .append(", ")
                    .append(column)
                    .append(" from ")
                    .append(schema)
                    .append(".")
                    .append(table);
        }

        List<Filter> filterList = dimQueryParam.getFilterList();
        filterList = removeNullFilter(filterList);
        String whereCondition = dimTable.getWhereCondition();

        DimType dimType = dim.getDimType();

        Filter authDimFilter = null;

        Boolean isAuth = dimQueryParam.isAuth();
        Boolean isGrade = dimQueryParam.isGrade();
        if (isAuth && !isGrade) {
            authDimFilter = this.getAuthDimFilter(dimQueryParam);
        }

        if (!CollectionUtils.isEmpty(filterList) || null != authDimFilter || DimType.STD_WITHOUT_TABLE.equals(dimType) || StringUtil.isNotEmpty(whereCondition)) {
            selDimSqlBuffer.append(" where (1=1 ");
            if (StringUtil.isNotEmpty(whereCondition)) {
                selDimSqlBuffer.append(" and ")
                                .append(whereCondition);
            }
        }

        //此处增加权限筛选所拥有的过滤项
        if (null != authDimFilter) {
            filterList.add(authDimFilter);
        }

        filterList = this.hasAuthFilter(filterList);

        if (filterList.size() > 0) {
            selDimSqlBuffer.append(" and (");
        }

        boolean firstFilter = true;
        for (Filter filter : filterList) {

            String filterCode = filter.getCode();
            Table filterTable = null;
            if (dimCode.equalsIgnoreCase(filterCode)) {
                filterTable = dimTable;
            } else {
                Dimension filterDim = indicatorService.getDimensionTableInfo(filterCode);
                filterTable = this.findDimensionTable(filterDim, table);
            }

            //维度key
            String filterPrimaryKey = filterTable.getDimPrimaryKey();
            String filterTableName = filterTable.getTableName();

            if (!table.equalsIgnoreCase(filterTableName)) {
                //维度表和事实表取的不是同一张表，则自动忽略。
                continue;
            }

            //筛选项
            boolean isFirstLogical = true;
            List<Operator> operatorList = filter.getOperatorList();
            if (!CollectionUtils.isEmpty(operatorList)) {

                if (firstFilter) {
                    firstFilter = false;
                } else {
                    selDimSqlBuffer.append(" and ");
                }
                selDimSqlBuffer.append(" (");

                for (Operator operator : operatorList) {

                    SqlOprType sqlOprType = operator.getSqlOprType();
                    SqlLogicalType sqlLogicalType = operator.getSqlLogicalType();
                    String logicalString = SqlLogicalType.OR.equals(sqlLogicalType) ? " or " : " and ";
                    if (isFirstLogical) {
                        logicalString = " ";
                        isFirstLogical = false;
                    }

                    List<String> valueList = operator.getDataList();
                    if (!CollectionUtils.isEmpty(valueList)) {
                        if (SqlOprType.LIKE.equals(sqlOprType)) {
                            //本身的操作
                            String textValue = valueList.get(0);//文本框搜索的值
                            textValue = BuildSqlServiceImpl.formatSqlValue(textValue);
                            if (StringUtil.isEmpty(textValue)) {
                                continue;
                            }
                            selDimSqlBuffer.append(logicalString).append("CONCAT(" + column + ", '')").append(" like '%").append(textValue).append("%'");

                        } else if (SqlOprType.IN.equals(sqlOprType)) {
                            //相关操作
                            List<String> vs = new ArrayList<>();
                            for (String value : valueList) {
                                value = BuildSqlServiceImpl.formatSqlValue(value);
                                vs.add("'" + value + "'");
                            }
                            String ids = StringUtil.join(vs, ",");
                            selDimSqlBuffer.append(logicalString).append("CONCAT(" + filterPrimaryKey + ", '')").append(" in (").append(ids).append(")");
                        }
                    }
                }
                selDimSqlBuffer.append(")");
            }
            firstFilter = false;

        }

        if (filterList.size() > 0) {
            selDimSqlBuffer.append(")");
        }

        if (!CollectionUtils.isEmpty(filterList) || DimType.STD_WITHOUT_TABLE.equals(dimType) || StringUtil.isNotEmpty(whereCondition)) {
            selDimSqlBuffer.append(")");
        }

        if (DimType.STD_WITHOUT_TABLE.equals(dimType)) {
            selDimSqlBuffer.append(" and code='" + dimCode + "'");
        }

        selDimSqlBuffer.append(" order by ").append(column).append(" desc ");

        return selDimSqlBuffer.toString();

    }

    private Table findDimensionTable(Dimension dim) {
        return this.findDimensionTable(dim, null);
    }

    private Table findDimensionTable(Dimension dim, String tableName) {

        Table dimTable = null;
        //维度表
        List<Table> dimTableList = dim.getDimTableList();
        if (!CollectionUtils.isEmpty(dimTableList)) {

            if (StringUtil.isNotEmpty(tableName)) {
                //如果table不为空，则找出根tableName一致的表作为维度表。
                for (Table table : dimTableList) {

                    if (tableName.equalsIgnoreCase(table.getTableName())) {
                        dimTable = table;
                        break;
                    }

                }

            } else {
                //维度值，一期默认只取第一个维度表
                dimTable = dimTableList.get(0);
            }

        }
        return dimTable;
    }

}
