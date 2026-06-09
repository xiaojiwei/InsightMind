package com.graphinsight.indicator.service.impl;

import com.alibaba.druid.sql.SQLUtils;
import com.alibaba.druid.sql.ast.SQLStatement;
import com.alibaba.druid.sql.dialect.mysql.visitor.MySqlSchemaStatVisitor;
import com.alibaba.druid.stat.TableStat;
import com.alibaba.druid.util.JdbcConstants;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.alibaba.fastjson.serializer.SimplePropertyPreFilter;
import com.baomidou.dynamic.datasource.annotation.DS;
import com.baomidou.dynamic.datasource.toolkit.DynamicDataSourceContextHolder;
import com.graphinsight.indicator.auto.mapper.MeasureMapper;
import com.graphinsight.indicator.constant.IndicatorConstant;
import com.graphinsight.indicator.dao.QueryDataSourceDao;
import com.graphinsight.indicator.enums.*;
import com.graphinsight.indicator.model.*;
import com.graphinsight.indicator.model.dto.AuthDimensionBloodCheckResult;
import com.graphinsight.indicator.model.dto.BaseInfoDTO;
import com.graphinsight.indicator.model.dto.OperateGrantValue;
import com.graphinsight.indicator.service.*;
import com.graphinsight.indicator.util.CloneUtils;
import com.graphinsight.indicator.util.MemCacheUtils;
import com.graphinsight.indicator.util.StringUtil;
import com.graphinsight.indicator.util.UserThreadLocalUtil;
import org.apache.commons.math.genetics.Fitness;
import org.apache.directory.api.util.Strings;
import org.apache.hadoop.yarn.webapp.hamlet.Hamlet;
import org.codehaus.jackson.map.Serializers;
import org.mortbay.util.ajax.JSON;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Lazy;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;

import javax.annotation.Resource;
import javax.management.Query;
import javax.swing.text.View;
import java.text.SimpleDateFormat;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.time.temporal.TemporalAdjusters;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
@Transactional
public class ChartQueryServiceImpl implements ChartQueryService {

    /**
     * 指标服务
     */
    @Autowired
    private IndicatorService indicatorService;

    @Autowired
    private SqlCheckService sqlCheckService;

    @Autowired
    private QueryDataSourceDao queryDataSourceDao;

    @Autowired
    private SpaceService spaceService;

    @Autowired
    private DimensionQueryService dimQueryService;

    /**
     * sql执行策略
     */
    @Autowired
    private SqlQueryStrategy sqlQueryStrategy;

    @Autowired
    private QueryPlanService queryPlanService;

    @Resource
    protected RedisCacheService redisCacheService;

    @Lazy
    @Autowired
    private SyncTaskService syncTaskService;

    @Autowired
    private IndicatorUserService indicatorUserService;

    @Autowired
    private AuthService authService;
    @Autowired
    MeasureMapper measureMapper;

    @Autowired
    @Qualifier("secondJdbcTemplate")
    private JdbcTemplate jdbcTemplate;

    @Override
    public JdbcTemplate getJdbcTemplate() {
        return this.jdbcTemplate;
    }

    @Override
    public RedisCacheService getRedisCacheService() {
        return this.redisCacheService;
    }

    @Override
    public String execOnlySingleMeasure(String measureCode, String dateCode, String dateInFilterParam, Set<String> dimSet, String username, Long spaceId) {

        PageData pageData = this.execMetaSingleMeasure(measureCode, dateCode, dateInFilterParam, dimSet, username, spaceId);
        String result = null;
        List<List<Cell>> cellList = pageData.getCellList();

        for (List<Cell> cells : cellList) {

            for (Cell cell : cells) {

                CellType cellType = cell.getType();
                if (CellType.MEASURE.equals(cellType)) {
                    return cell.getData();
                }

            }

        }

        return result;

    }

    @Override
    public PageData execMetaSingleMeasure(String measureCode, List<Filter> filterList, Set<String> dimCodeSet, String username, Long spaceId) {

        DataSource dataSource = new DataSource();
        dataSource.setUsername(username);
        dataSource.setSpaceId(spaceId);
        dataSource.setFilterList(filterList);
        dataSource.setChartType(ChartType.LINE);

        List<BaseConfigure> configureList = dataSource.getConfigureList();
        BaseConfigure measConfigConfig = new BaseConfigure();
        measConfigConfig.setCode(measureCode);

        configureList.add(measConfigConfig);

        for (String dimCode : dimCodeSet) {

            BaseConfigure dimConfigConfig = new BaseConfigure();
            dimConfigConfig.setCode(dimCode);

            configureList.add(dimConfigConfig);

        }

        PageData pageData = this.execQuery(dataSource, false);

        return pageData;
    }

    @Override
    public PageData execMetaSingleMeasure(String measureCode, String dateCode, String dateInFilterParam, Set<String> dimCodeSet, String username, Long spaceId) {

        List<Filter> filterList = new ArrayList<>();
        Filter filter = new Filter();

        filter.setCode(dateCode);
        List<Operator> operatorList = filter.getOperatorList();
        Operator operator = new Operator();
        //日期筛选条件
        operator.getDataList().add(dateInFilterParam);
        operator.setSqlOprType(SqlOprType.IN);
        operator.setTimeRange(TimeRange.DATE);
        operatorList.add(operator);

        filterList.add(filter);

        return this.execMetaSingleMeasure(measureCode, filterList, dimCodeSet, username, spaceId);
    }

    private String buildMd5Key(DataSource dataSource) {

        DataSource keyDataSoruce = CloneUtils.clone(dataSource);
        keyDataSoruce.setTraceId(null);
        keyDataSoruce.setCacheStrategy(null);
        keyDataSoruce.setUpdateDate(null);
        keyDataSoruce.setUpdater(null);
        keyDataSoruce.setCreateDate(null);

        SimplePropertyPreFilter filter = new SimplePropertyPreFilter();
        filter.getExcludes().add("updateDate");
        filter.getExcludes().add("updater");
        filter.getExcludes().add("createDate");

        String authKey = buildAuthCacheKeyInfo(dataSource);
        keyDataSoruce.setCode(authKey.toString());

        JSONObject keyJson = (JSONObject)JSONObject.toJSON(keyDataSoruce);

        String keyStr = JSONObject.toJSONString(keyJson, filter);
        String md5Key = "DSQ_" + StringUtil.encrypt(keyStr);

        redisCacheService.put("DS_" + md5Key, dataSource);

        return md5Key;

    }

    public String buildAuthCacheKeyInfo(DataSource dataSource) {

        String authKey = "";

        String userName = dataSource.getUsername();
        if (null == userName) {
            userName = UserThreadLocalUtil.getUserName();
        }

        //当前登录人在空间下配置的所有维度、指标权限
        Set<AuthElement> authElementSet = this.getAuthElementSet(dataSource.getSpaceId(), userName);
        if (!CollectionUtils.isEmpty(authElementSet)) {

            for (AuthElement authElement : authElementSet) {
                Date update = authElement.getUpdateDate();
                Filter filter1 = authElement.getFilter();
                if (null != filter1) {

                    List<Operator> operatorList = filter1.getOperatorList();

                    for (Operator operator : operatorList) {

                        List<String> dataList = operator.getDataList();
                        if (!CollectionUtils.isEmpty(dataList)) {

                            for (String data : dataList) {

                                if (data.indexOf("#") == 0) {

                                    String dataId = data.replaceFirst("#", "");
                                    List<String> contextDataList = this.applyAuthContextDataList(dataId, userName);
                                    if (!CollectionUtils.isEmpty(contextDataList)) {
                                        for (String context : contextDataList) {
                                            authKey += context;
                                        }
                                    }

                                } else {
                                    authKey += data;
                                }

                            }
                        }
                    }
                }
                if (null != update) {
                    authKey += update.getTime();
                }
            }

        }

        return authKey;

    }

    @Override
    public PageData execQuery(DataSource dataSource) {
        return this.execQuery(dataSource, false);
    }
    
    private CacheStrategy getCacheStrategy(DataSource dataSource) {

        CacheStrategy cacheStrategy = dataSource.getCacheStrategy();

        List<BaseConfigure> configureList = dataSource.getConfigureList();
        return cacheStrategy;

    }

    public void addQueryLog(DataSource dataSource, PageData pageData) {
        /*

        final String userName = UserThreadLocalUtil.getUserName();
        CompletableFuture.runAsync(() -> {

            QueryDataSource queryDataSource = new QueryDataSource();
            queryDataSource.setCreator(userName);
            queryDataSource.setSpaceId(String.valueOf(dataSource.getSpaceId()));
            List<BaseConfigure> configureList = dataSource.getConfigureList();

            for (BaseConfigure baseConfigure : configureList) {
                QueryBaseConfigure queryBaseConfigure = new QueryBaseConfigure();
                queryBaseConfigure.setCode(baseConfigure.getCode());
                queryBaseConfigure.setCreator(userName);

                queryDataSource.getConfigureList().add(queryBaseConfigure);
            }

            String sql = pageData.getReviewSql();

            List<SQLStatement> sqlStatements = SQLUtils.parseStatements(sql, JdbcConstants.MYSQL.name());

            for (SQLStatement sqlStatement : sqlStatements) {
                MySqlSchemaStatVisitor mySqlSchemaStatVisitor = new MySqlSchemaStatVisitor();
                sqlStatement.accept(mySqlSchemaStatVisitor);

                Map<TableStat.Name, TableStat> tableMap = mySqlSchemaStatVisitor.getTables();
                Set tableNameSet = tableMap.keySet();
                for (Object o : tableNameSet) {
                    String name = String.valueOf(o);
                    QueryBaseTable queryBaseTable = new QueryBaseTable();
                    queryBaseTable.setTableName(name);
                    queryBaseTable.setCreator(userName);
                    queryDataSource.getQueryTableList().add(queryBaseTable);
                }
            }

            this.queryDataSourceDao.save(queryDataSource);
        });

         */

    }

    @Override
    public PageData execQuery(DataSource dataSource, boolean isSyncUpdate) {
        Long begin = System.currentTimeMillis();
        /**
         * 缓存策略
         */
        CacheStrategy cacheStrategy = this.getCacheStrategy(dataSource);
        PageData pageData = null;
        String md5Key = this.buildMd5Key(dataSource);
//        isSyncUpdate = false;
//        dataSource.setCacheStrategy(CacheStrategy.OVERWRITE);
        if (StringUtil.isNotEmpty(dataSource.getMd5()) && isSyncUpdate) {
            md5Key = dataSource.getMd5();
            dataSource.setCacheStrategy(CacheStrategy.QUERY_UPDATE);
//            dataSource.setCacheStrategy(CacheStrategy.OVERWRITE);
        }
        if (isSyncUpdate) {
            //系统内部自己的异步调用方法，只更新缓存,无返回。
            dataSource.setCacheStrategy(CacheStrategy.OVERWRITE);
            this.query(md5Key, dataSource);
            return null;
        }

//        if (CacheStrategy.OVERWRITE.equals(cacheStrategy) || true) {
        if (CacheStrategy.OVERWRITE.equals(cacheStrategy)) {
            pageData = this.query(md5Key, dataSource);
            queryPlanService.supQueryPlan(md5Key, pageData);
        } else if (CacheStrategy.DELETE.equals(cacheStrategy)) {
            this.queryPlanService.delete(md5Key);
        } else {

            //从缓存获取数据
            pageData = this.queryPlanService.getData(md5Key);
            boolean isQuery = false;
            if (null == pageData) {
                //缓存中无结果则直接查询
                pageData = this.query(md5Key, dataSource);
                queryPlanService.addCache(md5Key, pageData, Long.valueOf(1));
                isQuery = true;
            } else {
                pageData.setUseCache(true);
            }

//            queryPlanService.supQueryPlan(md5Key, pageData);
            dataSource.setMd5(md5Key);

            //需要异步更新数据
            if (CacheStrategy.QUERY_UPDATE.equals(cacheStrategy) && !isQuery) {
                String userName = UserThreadLocalUtil.getUserName();
                dataSource.setUsername(userName);
                syncTaskService.syncUpdate(dataSource);
            }

        }

        return pageData;

    }

    public PageData query(String md5Key, DataSource dataSource) {

        Long begin = System.currentTimeMillis();
        UserThreadLocalUtil.setBeginTime();
        QueryParam queryParam = this.buildQueryParam(dataSource);
        UserThreadLocalUtil.printCost("QueryParam");

        PageData pageData = this.execQuery(queryParam);

        //总耗时
        Long cost = System.currentTimeMillis() - begin;

        UserThreadLocalUtil.printCost("Query Cost");

        CacheStrategy cacheStrategy = this.getCacheStrategy(dataSource);
        if (CacheStrategy.QUERY_UPDATE.equals(cacheStrategy) || CacheStrategy.OVERWRITE.equals(cacheStrategy)) {
            queryPlanService.addCache(md5Key, pageData, cost);
        }

        UserThreadLocalUtil.printCost("Query Cache");

        return pageData;

    }

    @Override
    public PageData execCountQuery(DataSource dataSource) {

        String queryCountId = dataSource.getQueryCountId();
        PageData result = null;
        String resultCacheKey = "_key_cnt_result_" + queryCountId;

        // 单清理缓存
        if (CacheStrategy.DELETE.equals(dataSource.getCacheStrategy())) {
            result = new PageData();
            result.setCacheKey(resultCacheKey);
            redisCacheService.delete(resultCacheKey);
            return result;
        }

        //优先查询缓存,缓存失败后走直连
        result = redisCacheService.get(resultCacheKey, PageData.class);

        if (null == result || CacheStrategy.OVERWRITE.equals(dataSource.getCacheStrategy())) {

            BuildSqlTuple tuple = new BuildSqlTuple();
            QueryParam queryParam = new QueryParam();
            queryParam.setQueryCountId(queryCountId);

            tuple.setQueryParam(queryParam);

            DataQueryService dataQuery = sqlQueryStrategy.getSqlQueryMethod(DataSetType.COUNT);
            result = dataQuery.queryData(tuple, new PageData());

            redisCacheService.put(resultCacheKey, result);

        } else {

            String jsonStr = redisCacheService.get(resultCacheKey, String.class);
            JSONObject jsonObj = (JSONObject) JSONArray.parse(jsonStr);
            JSONObject jsonInfo = (JSONObject) jsonObj.get("pageInfo");

            PageInfo pageInfo = result.getPageInfo();

            if (null != jsonInfo) {

                pageInfo.setPageStartRow(jsonInfo.getIntValue("pageStartRow"));
                pageInfo.setHasNextPage(jsonInfo.getBoolean("hasNextPage"));
                pageInfo.setPageEndRow(jsonInfo.getInteger("pageEndRow"));
                pageInfo.setTotalPages(jsonInfo.getInteger("totalPages"));
                pageInfo.setHasPreviousPage(jsonInfo.getBoolean("hasPreviousPage"));
                pageInfo.setTotalRows(jsonInfo.getInteger("totalRows"));
                pageInfo.setCurrentPage(jsonInfo.getInteger("currentPage"));
                pageInfo.setPageRecorders(jsonInfo.getInteger("pageRecorders"));

            }

            result.setCacheKey(resultCacheKey);
            result.setUseCache(true);

        }

        return result;

    }

    public Set<AuthElement> getAuthElementSet(Long spaceId, String userName, Boolean isDetail) {
        Set<AuthElement> authElementSet = new HashSet<>();
        if (null != spaceId) {
            //将当前登录人的所有指标、维度权限获取
            if (null != spaceId) {
                authElementSet.addAll(spaceService.getAuthElementBySpaceId(spaceId, userName, isDetail));
            }

        }
        return authElementSet;
    }

    /**
     * 构建
     * @see DataSource
     * @return
     */
    private QueryParam buildQueryParam(DataSource dataSource) {

        QueryParam queryParam = new QueryParam();

        // 临时展示pv数据使用
        queryParam.setChartShow(dataSource.isChartShow());

        //行列汇总
        queryParam.setRowSum(dataSource.isRowSum());
        queryParam.setColSum(dataSource.isColSum());

        String userName = dataSource.getUsername();
        if (null == userName) {
            userName = UserThreadLocalUtil.getUserName();
        }

        dataSource.setUsername(userName);
        queryParam.setUsername(userName);
        queryParam.setDirectQuery(dataSource.isDirectQuery());

        //当前登录人在空间下配置的所有维度、指标权限
        Set<AuthElement> authElementSet = this.getAuthElementSet(dataSource.getSpaceId(), userName, dataSource.isMeasureDetail());

        queryParam.setAuthElementSet(authElementSet);

        queryParam.setTaskId(dataSource.getTaskId());
        //交叉表是否需要分页
        queryParam.setPageable(dataSource.isPageable());
        //图标类型、数据类型
        queryParam.setChartType(dataSource.getChartType());
        if (dataSource.isDownFile()) {
            //如果是下载则数据类型设置为异步文件
            queryParam.setDataSetType(DataSetType.SYNCFILE);
        }

        //指标明细
        queryParam.setMeasureDetail(dataSource.isMeasureDetail());

        //设置数据源Id
        queryParam.setDataSourceId(dataSource.getDataSourceId());
        queryParam.setDataSource(dataSource);
        queryParam.setSourceType(dataSource.getSourceType());
        //设置查询count唯一标识
        queryParam.setQueryCountId(dataSource.getQueryCountId());

        //是否onlySql
        queryParam.setOnlySql(dataSource.isOnlySql());
        //是否含有指标操作，含有指标操作的sql需要对全部数据进行扫描，性能低。
        //如果不含有指标操作，只需要对维度进行排序或筛选，生成的sql可以进行sql优化，性能高。
        boolean hasMeasOpr = this.hasMeasOpr(dataSource);
        queryParam.setHasMeasOpr(hasMeasOpr);

        //指标维度集合
        List<BaseConfigure> allDimMeasList = this.getAllConfigureList(dataSource.getConfigureList(), new LinkedList<BaseConfigure>());
        //判断参数中是否含有自定义维度排序
        boolean hasDimConfigOrder = this.hasDimConfigOrder(allDimMeasList);
        queryParam.setHasDimConfigOrder(hasDimConfigOrder);

        boolean hasFilterTree = !CollectionUtils.isEmpty(dataSource.getFilterTreeList());
        queryParam.setHasFilterTree(hasFilterTree);

        //所含所有维度、指标。
        queryParam.setAllConfigureList(allDimMeasList);
        //将维度集合设置到查询参数中
        queryParam.setDimensionConfigureList(this.getDimConfigList(allDimMeasList));
        //将指标集合设置到查询参数中
        queryParam.setMeasureConfigureList(this.getMeasConfigList(allDimMeasList));

        //行轴
        List<BaseConfigure> rowAxisList = this.getAxisList(dataSource.getConfigureList(), AxisType.ROW);
        queryParam.setRowAxisList(rowAxisList);

        //列轴
        List<BaseConfigure> columnAxisList = this.getAxisList(dataSource.getConfigureList(), AxisType.COLUMN);
        queryParam.setColumnAxisList(columnAxisList);

        //页面大小
        queryParam.setPageSize(dataSource.getPageSize());
        //第几页
        queryParam.setPageNo(dataSource.getPageNo());;
        List<Filter> paramFilterList = dataSource.getFilterList();

        List<Filter> allFilterList = new ArrayList<>();

        //将登录人所拥有的部门组织权限增加到筛选项。
        allFilterList = this.applyByAuth(allFilterList, queryParam);

        List<Filter> allWhereFilterList = new ArrayList<>();
        List<Filter> allTreeFilterList = new ArrayList<>();
        allTreeFilterList.addAll(allFilterList);
        allWhereFilterList.addAll(allFilterList);

        List<FilterTree> filterTreeList = dataSource.getFilterTreeList();

        boolean isTreeFilter = false;
        if (!CollectionUtils.isEmpty(filterTreeList)) {
            isTreeFilter = true;
            for (FilterTree filterTree : filterTreeList) {
                this.findAllFilter(filterTree, allTreeFilterList);
            }
        }

        //当前端请求的筛选项有内容时，才加入过滤，否则忽略。
        if (!CollectionUtils.isEmpty(dataSource.getFilterList())) {
            for (Filter filter : dataSource.getFilterList()) {
                List<Operator> operatorList = filter.getOperatorList();

                if (!CollectionUtils.isEmpty(operatorList)) {

                    for (Operator operator : operatorList) {

                        List<String> dataList = operator.getDataList();
                        if (!CollectionUtils.isEmpty(dataList)) {
                            allWhereFilterList.add(filter);
                            break;
                        }

                    }
                }
            }
        }

        queryParam.setTreeFilter(isTreeFilter);
        List<Filter> filterList = this.getFilters(allWhereFilterList, userName, isTreeFilter);

        if (queryParam.isMeasureDetail()) {
            allWhereFilterList = removeMeasure(allWhereFilterList);
        }

        List<Filter> treeFilterList = this.getFilters(allTreeFilterList, userName, isTreeFilter);
        //where筛选项目
        queryParam.setFilterList(allWhereFilterList);
        //树筛选集合
        queryParam.setTreeFilterList(treeFilterList);

        queryParam.setFilterTreeList(dataSource.getFilterTreeList());

        //设置所有排序信息
        queryParam.setOrderList(this.getOrderList(dataSource));
        //明细指标排序筛选
        queryParam.setDetailOrderList(dataSource.getDetailOrderList());

        return queryParam;

    }

    private List<Filter> removeMeasure(List<Filter> filterList) {
        List<Filter> removeFilterList = new LinkedList<>();
        for (Filter filter : filterList) {
            if (!isMeasure(filter)) {
                removeFilterList.add(filter);
            }
        }

        return removeFilterList;
    }

    /**
     *
     * @param filterTree
     * @param allFilterList
     * @return
     */
    private List<Filter> findAllFilter(FilterTree filterTree, List<Filter> allFilterList) {

        FilterType filterType = filterTree.getFilterType();
        if (FilterType.FILTER.equals(filterType)) {
            allFilterList.add(filterTree.getFilter());
        } else if (FilterType.CHILDREN.equals(filterType)) {
            Set<FilterTree> filterTreeSet = filterTree.getFilterTreeSet();
            for (FilterTree fTree : filterTreeSet) {
                this.findAllFilter(fTree, allFilterList);
            }
        }

        return allFilterList;
    }

    private Set<String> getCodes(List objectList) {

        Set<String> codeSet = new LinkedHashSet<String>();
        for (Object object : objectList) {
            if (object instanceof BaseConfigure) {
                BaseConfigure baseConfigure = (BaseConfigure)object;
                if (baseConfigure.getCode().indexOf("MEAS_LDX") >= 0 || StringUtil.isNotEmpty(baseConfigure.getExpression())) {
                    //ldx指标处理
                } else {
                    codeSet.add(baseConfigure.getCode());
                }
            }
        }

        return codeSet;

    }

    private boolean isExist(List<AuthDimensionBloodCheckResult> authDimBolldList, String code) {
        boolean isHasBlood = false;
        for (AuthDimensionBloodCheckResult authDimensionBloodCheckResult : authDimBolldList) {

            if (code.equalsIgnoreCase(authDimensionBloodCheckResult.getAuthDimensionCode())) {
                isHasBlood = authDimensionBloodCheckResult.isHasBlood();
                break;
            }

        }
        return isHasBlood;
    }

    /**
     * 查找筛选条件中指定维度为in的。
     * @param allFilterList
     * @param code
     * @return
     */
    private Operator findInFilter(List<Filter> allFilterList, String code) {

        for (Filter filter : allFilterList) {

            if (code.equalsIgnoreCase(filter.getCode())) {

                List<Operator> operatorList = filter.getOperatorList();
                for (Operator operItr : operatorList) {

                    //维度筛选项所应用的指标，此处获取指标信息是排除单独给指标的筛选。
                    Set<AuthElementMeasure> authElementMeasureSet = filter.getAuthElementMeasureSet();
                    SqlOprType sqlOprType = operItr.getSqlOprType();

                    if (SqlOprType.IN.equals(sqlOprType) && CollectionUtils.isEmpty(authElementMeasureSet)) {
                        return operItr;
                    }

                }

            }

        }

        return null;

    }

    private boolean effective(Filter filter) {
        if (null != filter && null != filter.getOperatorList()) {

            List<Operator> operatorList = filter.getOperatorList();
            if (!CollectionUtils.isEmpty(operatorList)) {
                for (Operator operator : operatorList) {
                    if (CollectionUtils.isEmpty(operator.getDataList())) {
                        return false;
                    }
                }
            }
        }

        return true;
    }

    /**
     * 将授权的筛选条件添加，如果筛选条件是全局的需要与现存的筛选条件进行合并。
     * 合并条件是in
     * @param allFilterList
     * @param authFilter
     * @return
     */
    private List<Filter> addFilter(List<Filter> allFilterList, Filter authFilter) {

        if (!effective(authFilter)) {
            return allFilterList;
        }

        Set<AuthElementMeasure> authElementMeasureSet = authFilter.getAuthElementMeasureSet();
        //此为全局筛选项，需要合并
        if (CollectionUtils.isEmpty(authElementMeasureSet)) {

            Operator operator = this.findInFilter(allFilterList, authFilter.getCode());
            if (null == operator) {
                //不存在直接添加即可
                allFilterList.add(authFilter);
            } else {
                //存在可以合并的为in的全局筛选项。
                for (Operator authOpera : authFilter.getOperatorList()) {

                    if (SqlOprType.IN.equals(authOpera.getSqlOprType())) {

                        for (String authData : authOpera.getDataList()) {

                            if (!operator.getDataList().contains(authData)) {
                                operator.getDataList().add(authData);
                            }

                        }

                    }

                }

            }

        } else {
            //只作用于选定的指标结合，无需合并。
            allFilterList.add(authFilter);
        }

        return allFilterList;

    }

    public static Filter deelpCopy(Filter filter) {
        Filter deelpCopyFilter = null;
        try {
            List<Operator> operatorList = filter.getOperatorList();
            if (!CollectionUtils.isEmpty(operatorList)) {
                for (Operator operator : operatorList) {
                    operator.getDataList();
                }
            }
            deelpCopyFilter = (Filter)filter.deepClone();
        } catch (Exception ex) {
            throw new RuntimeException("序列化深拷贝错误");
        }
        return deelpCopyFilter;
    }

    /**
     * 将所有权限维度与过当前筛选有血缘关系的维度统一添加
     * @param allFilterList
     * @param authElementSet
     * @return
     */
    private List<Filter> applyByAuth(List<Filter> allFilterList, QueryParam queryParam) {

        boolean measureDetail = queryParam.getDataSource().isMeasureDetail();

        //当前登录入在空间下的所有指标、维度权限
        Set<AuthElement> authElementSet = queryParam.getAuthElementSet();
        List<MeasureConfigure> measureConfigureList = queryParam.getMeasureConfigureList();

        Set<String> measCodeSet = this.getMeasCodes(queryParam);
        Set<String> dimCodeSet = this.getDimCodes(queryParam);

        for (AuthElement authElement : authElementSet) {

            AuthElementType authEleType = authElement.getAuthElementType();
            //code
            String code = authElement.getCode();

            if (AuthElementType.DIMENSION.equals(authEleType)) {

                Set<String> authCodeSet = new HashSet();
                String authCode = authElement.getCode();
                authCodeSet.add(authCode);

                //to do 血缘判断 根据权限code确认血缘
                List<AuthDimensionBloodCheckResult> authDimBolldList = indicatorService.checkBloodByAuthDimension(authCodeSet, dimCodeSet, measCodeSet);

                if (this.isExist(authDimBolldList, code)) {

                    dimCodeSet.add(authCode);
                    //此处应根据维度code以及操作方式为in的数据进行合并filter dataList合并。
                    Filter filter = null;
                    filter = authElement.getFilter();
                    filter = deelpCopy(filter);
                    filter.setAuthFilterParamType(authElement.getAuthFilterParamType());
                    //指标赋予
                    filter.setAuthElementMeasureSet(authElement.getAuthElementMeasureSet());

                    //将筛选项添加到全局筛选中。
                    allFilterList = this.addFilter(allFilterList, filter);

                    if (measureDetail) {
                        filter = authElement.getDetailFilter();
                        if (null != filter) {
                            filter.setIsDetail(true);
                            filter = deelpCopy(filter);
                            filter.setAuthFilterParamType(authElement.getAuthFilterParamType());
                            //指标赋予
                            filter.setAuthElementMeasureSet(authElement.getAuthElementMeasureSet());

                            //将筛选项添加到全局筛选中。
                            allFilterList = this.addFilter(allFilterList, filter);
                        }
                    }

                }

            } else {

                //指标筛选条件添加到目标值中。
                for (MeasureConfigure measureConfigure : measureConfigureList) {
                    if (measureConfigure.getCode().equalsIgnoreCase(code)) {
                        Filter filter = authElement.getFilter();
                        if (null != filter) {

                            Set<String> authCodeSet = new HashSet();
                            //指标的筛选条件判断是否符合血缘，符合的添加到allFilterList中。
                            authCodeSet.add(filter.getCode());
                            //to do 血缘判断 根据权限code确认血缘
                            List<AuthDimensionBloodCheckResult> authDimBolldList = indicatorService.checkBloodByAuthDimension(authCodeSet, dimCodeSet, measCodeSet);

                            if (this.isExist(authDimBolldList, code)) {
                                filter.setAuthFilterParamType(authElement.getAuthFilterParamType());
                                allFilterList.add(filter);
                            }

                        }

                    }

                }
                
            }

        }

        return allFilterList;
    }

    /**
     * 当前登录人所拥有的filter集合
     * @param dataSource
     * @return
     */
    private List<Filter> authFilter(DataSource dataSource) {
        Space space = dataSource.getSpace();
        return null;

    }

    private List<Filter> getFilters(List<Filter> filterList, String username, boolean isTreeFilter) {

        List<Filter> delFilterList = new ArrayList<>();
        for (Filter filter : filterList) {

            ViewType viewType = filter.getViewType();

            List<Operator> operatorList = filter.getOperatorList();
            for (Operator operator : operatorList) {
                if (TimeRange.DATE.equals(operator.getTimeRange()) && !ViewType.HOUR.equals(viewType)) {
                    try {
                        this.initDayTimeRange(operator, filter, isTreeFilter);
                    } catch (Exception ex) {
                        ex.printStackTrace();
                        delFilterList.add(filter);
                    }
                }
            }

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
                            List<String> contextDataList = this.applyAuthContextDataList(dataId, username);
                            if (!CollectionUtils.isEmpty(contextDataList)) {
                                addList.addAll(contextDataList);
                            } else {
                                delFilterList.add(filter);
                            }

                        }

                    }

                    dataList.removeAll(delList);
                    dataList.addAll(addList);

                }

            }

        }

        filterList.removeAll(delFilterList);

        return filterList;

    }

    /**
     * 获取指定人员上下文数据
     * @param data
     * @param username
     * @return
     */
    public List<String> applyAuthContextDataList(String data, String username) {

        OperateGrantValue operateGrantValue = indicatorUserService.getOperateGrantValue(username, Long.valueOf(data));
        List<String> contextDataList = new ArrayList<>();
        contextDataList.addAll(operateGrantValue.getKeys());

        return contextDataList;

    }

    @Override
    public Set<AuthElement> getAuthElementSet(Long spaceId, String userName) {
        return this.getAuthElementSet(spaceId, userName, false);
    }

    public void initDayTimeRange(Operator operator, Filter filter, boolean isTreeFilter) {

        String begin = null;
        LocalDate endDate = LocalDate.now();
        DateTimeFormatter dtf = DateTimeFormatter.ofPattern("yyyy-MM-dd");
        String end = endDate.format(dtf);
        TimeRange timeRange = operator.getTimeRange();
        LocalDate currentDate = LocalDate.now();
//        currentDate = currentDate.minusDays(1);
        if (TimeRange.WEEK.equals(timeRange)) {
            //近7天
            LocalDate beginDate = currentDate.minusDays(6);
            begin = beginDate.format(dtf);

        } else if (TimeRange.ONE_MONTH.equals(timeRange)) {
            //近1月
            LocalDate beginDate = currentDate.minusMonths(1);
            begin = beginDate.format(dtf);

        } else if (TimeRange.TRIPLE_MONTH.equals(timeRange)) {
            //近3月
            LocalDate beginDate = currentDate.minusMonths(3);
            begin = beginDate.format(dtf);

        } else if (TimeRange.HALF_YEAR.equals(timeRange)) {
            //近半年
            LocalDate beginDate = currentDate.minusMonths(6);
            begin = beginDate.format(dtf);
        } else if (TimeRange.ONE_YEAR.equals(timeRange)) {
            //近一年
            LocalDate beginDate = currentDate.minusMonths(12);
            begin = beginDate.format(dtf);
        } else if (TimeRange.YESTERDAY.equals(timeRange)) {
            //昨天
            LocalDate beginDate = currentDate;
            begin = beginDate.format(dtf);
        } else if (TimeRange.DATE.equals(timeRange)) {

            SqlOprType sqlOprType = operator.getSqlOprType();
            if (SqlOprType.BETEEN.equals(sqlOprType)) {

                List<String> dataList = operator.getDataList();
                begin = dataList.get(0);
                begin = this.getRealDateStr(begin, filter, true, isTreeFilter);

                end = dataList.get(1);
                end = this.getRealDateStr(end, filter, false, isTreeFilter);

            } else if (SqlOprType.GREATER_THAN_OR_EQUAL.equals(sqlOprType)) {

                List<String> dataList = operator.getDataList();
                begin = dataList.get(0);
                begin = this.getRealDateStr(begin, filter, true, isTreeFilter);

            } else if (SqlOprType.SMALLER_THAN_OR_EQUAL.equals(sqlOprType)) {

                List<String> dataList = operator.getDataList();
                end = dataList.get(0);
                end = this.getRealDateStr(end, filter, false, isTreeFilter);

            } else if (SqlOprType.IN.equals(sqlOprType)) {

                operator.setSqlOprType(SqlOprType.BETEEN);
                List<String> dataList = operator.getDataList();
                begin = dataList.get(0);
                begin = this.getRealDateStr(begin, filter, true, isTreeFilter);

                end = dataList.get(0);
                end = this.getRealDateStr(end, filter, false, isTreeFilter);

            }

        } else {
            //如果没有匹配上按最近7天处理。
            LocalDate beginDate = currentDate.minusDays(7);
            begin = beginDate.format(dtf);
        }

        operator.setBegin(begin);
        operator.setEnd(appendDateUpperBoundSuffix(end));
    }

    private String appendDateUpperBoundSuffix(String end) {
        if (StringUtil.isEmpty(end)) {
            return end;
        }
        // 标准日粒度 yyyy-MM-dd 已经是 buildFilterColumn(date_format(..., '%Y-%m-%d'))
        // 的同口径结果，不能再拼 9，否则会生成 2026-05-289 这类非法边界。
        if (end.matches("\\d{4}-\\d{2}-\\d{2}")) {
            return end;
        }
        // 兼容历史上带时分秒或更高精度的字符串型时间边界。
        return end + "9";
    }

    private final static int OPERATOR = 1;
    private final static int CONSTANT = 2;

    public static String removeChinese(String data) {
        String chinese = "[\u4e00-\u9fa5]";
        Pattern p = Pattern.compile(chinese);
        Matcher m = p.matcher(data);
        String v = m.replaceAll("");
        return v;
    }

    public static LocalDate formatDateStyle(ViewType viewType, boolean isBegin, String data) {

        LocalDate currentDate = null;
        data = removeChinese(data);

        if (ViewType.DAY.equals(viewType)) {

            String[] datas = data.split("-");
            Integer year = Integer.valueOf(datas[0]);
            Integer month = Integer.valueOf(datas[1]);
            Integer day = Integer.valueOf(datas[2]);

            currentDate = LocalDate.of(year, month, day);

        } else if (ViewType.WEEK.equals(viewType)) {

            String yearStr = data.substring(0, 4);
            String weekStr = data.substring(4).replaceFirst("-W", "");

            Integer year = Integer.valueOf(yearStr);
            Integer week = Integer.valueOf(weekStr);

            Calendar cal = Calendar.getInstance();
            cal.setFirstDayOfWeek(Calendar.MONDAY);
            cal.set(Calendar.YEAR, year);
            cal.set(Calendar.WEEK_OF_YEAR, week);

            if (isBegin) {
                cal.set(Calendar.DAY_OF_WEEK, cal.getFirstDayOfWeek());
            } else {
                cal.set(Calendar.DAY_OF_WEEK, cal.getFirstDayOfWeek() + 6);
            }

            Date date = cal.getTime();
            currentDate = date.toInstant().atZone(ZoneId.systemDefault()).toLocalDate();

        } else if (ViewType.MONTH.equals(viewType)) {

            Integer year = 0;
            Integer month = 0;

            if (data.indexOf("-") > 0) {
                String[] datas = data.split("-");
                year = Integer.valueOf(datas[0]);
                month = Integer.valueOf(datas[1]);
            } else {

                data = data.replaceAll("-", "");
                String yearStr = data.substring(0, 4);
                String monthStr = data.substring(4);

                year = Integer.valueOf(yearStr);
                month = Integer.valueOf(monthStr);

            }

            currentDate = LocalDate.of(year, month, 1);

            if (!isBegin) {
                //最后一天
                LocalDate lastDay = currentDate.with(TemporalAdjusters.lastDayOfMonth());
                currentDate = lastDay;
            }

        } else if (ViewType.SEASON.equals(viewType)) {

            data = data.replaceAll("-", "");
            String yearStr = data.substring(0, 4);
            String quarterStr = data.substring(4);

            quarterStr = quarterStr.replaceAll("Q", "");

            Integer year = Integer.valueOf(yearStr);
            Integer quarter = Integer.valueOf(quarterStr);

            Integer monthStep = quarter * 3;

            currentDate = LocalDate.of(year, monthStep, 1);
            Month month = currentDate.getMonth();
            //季度第一个月
            Month firstMonthOfQuarter = month.firstMonthOfQuarter();
            //季度月的第一天
            LocalDate firstDayOfMonthQuarter = LocalDate.of(currentDate.getYear(), firstMonthOfQuarter, 1);
            if (isBegin) {
                currentDate = firstDayOfMonthQuarter;
            } else {
                LocalDate lastDayOfMonthQuarter = firstDayOfMonthQuarter.plusMonths(2);
                //季最后一天
                LocalDate lastDay = lastDayOfMonthQuarter.with(TemporalAdjusters.lastDayOfMonth());
                currentDate = lastDay;
            }

        } else if (ViewType.YEAR.equals(viewType)) {

            data = data.replaceAll("-", "");
            Integer year = Integer.valueOf(data);
            currentDate = LocalDate.of(year, 1, 1);
            LocalDate firstDayOfYear = currentDate.with(TemporalAdjusters.firstDayOfYear());
            if (isBegin) {
                currentDate = firstDayOfYear;
            } else {
                //最后一天
                LocalDate lastDay = firstDayOfYear.with(TemporalAdjusters.lastDayOfYear());
                currentDate = lastDay;
            }
        }

        return currentDate;
    }

    private String getRealDateStr(String orgDate, Filter filter, boolean isBegin, boolean isTreeFilter) {

        if (isTreeFilter && orgDate.indexOf("T") < 0) {
            return orgDate;
        }

        ViewType viewType = ViewType.DAY;

        String dimCode = filter.getCode();
//        Dimension dim = indicatorService.getDimensionTableInfo(dimCode);
        Dimension dim = MemCacheUtils.getDimensionTableInfo(this.indicatorService, dimCode);
        if (null != dim) {
            viewType = dim.getViewType();
        }

        String realDate = orgDate;
        DateTimeFormatter dtf = DateTimeFormatter.ofPattern("yyyy-MM-dd");
        LocalDate currentDate = LocalDate.now();

        if (!StringUtil.isEmpty(orgDate) && orgDate.indexOf("T") >= 0) {

            String[] dates = orgDate.split("-");
            String operator = dates[OPERATOR];
            String constant = dates[CONSTANT];

            Integer deviationNumber = Integer.valueOf(constant);

            if (ViewType.DAY.equals(viewType)) {
                if ("M".equalsIgnoreCase(operator)) {
                    currentDate = currentDate.minusDays(deviationNumber);
                } else {
                    currentDate = currentDate.plusDays(deviationNumber);
                }
            } else if (ViewType.WEEK.equals(viewType)) {
                if ("M".equalsIgnoreCase(operator)) {
                    currentDate = currentDate.minusWeeks(deviationNumber);
                } else {
                    currentDate = currentDate.plusWeeks(deviationNumber);
                }

                LocalDate monday = currentDate.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
                LocalDate sunday = currentDate.with(TemporalAdjusters.nextOrSame(DayOfWeek.SUNDAY ));

                if (isBegin) {
                    currentDate = monday;
                } else {
                    //最后一天
                    currentDate = sunday;
                }

            } else if (ViewType.MONTH.equals(viewType)) {
                if ("M".equalsIgnoreCase(operator)) {
                    currentDate = currentDate.minusMonths(deviationNumber);
                } else {
                    currentDate = currentDate.plusMonths(deviationNumber);
                }

                LocalDate firstDayOfMonth = currentDate.with(TemporalAdjusters.firstDayOfMonth());
                if (isBegin) {
                    currentDate = firstDayOfMonth;
                } else {
                    //最后一天
                    LocalDate lastDay = firstDayOfMonth.with(TemporalAdjusters.lastDayOfMonth());
                    currentDate = lastDay;
                }

            } else if (ViewType.SEASON.equals(viewType)) {

                Month month = currentDate.getMonth();
                //本季度第一个月
                Month firstMonthOfQuarter = month.firstMonthOfQuarter();
                //季度月的第一天
                LocalDate firstDayOfMonthQuarter = LocalDate.of(currentDate.getYear(), firstMonthOfQuarter, 1);
                //季度月数进制月数
                Integer step = 3 * deviationNumber;
                LocalDate monthQuarter = null;
                if ("M".equalsIgnoreCase(operator)) {
                    monthQuarter = firstDayOfMonthQuarter.minusMonths(step);
                } else {
                    monthQuarter = firstDayOfMonthQuarter.plusMonths(step);
                }

                //季度月的第一天
                LocalDate tagetFirstDayOfMonthQuarter = LocalDate.of(monthQuarter.getYear(), monthQuarter.getMonth(), 1);
                if (isBegin) {
                    currentDate = tagetFirstDayOfMonthQuarter;
                } else {
                    LocalDate tagetLastDayOfMonthQuarter = tagetFirstDayOfMonthQuarter.plusMonths(2);
                    //本季最后一天
                    LocalDate lastDay = tagetLastDayOfMonthQuarter.with(TemporalAdjusters.lastDayOfMonth());
                    currentDate = lastDay;
                }

            } else if (ViewType.YEAR.equals(viewType)) {
                if ("M".equalsIgnoreCase(operator)) {
                    currentDate = currentDate.minusYears(deviationNumber);
                } else {
                    currentDate = currentDate.plusYears(deviationNumber);
                }

                LocalDate firstDayOfYear = currentDate.with(TemporalAdjusters.firstDayOfYear());
                if (isBegin) {
                    currentDate = firstDayOfYear;
                } else {
                    //最后一天
                    LocalDate lastDay = firstDayOfYear.with(TemporalAdjusters.lastDayOfYear());
                    currentDate = lastDay;
                }
            }

        } else {
            currentDate = this.formatDateStyle(viewType, isBegin, realDate);
        }

        realDate = currentDate.format(dtf);

        return realDate;

    }

    /**
     * 获取所有排序集合
     * @param dataSource
     * @return
     */
    private List<Order> getOrderList(DataSource dataSource) {

        List<Order> orderList = new LinkedList<>();
        List<BaseConfigure> configureList = dataSource.getConfigureList();

        for (BaseConfigure baseConfigure : configureList) {

            Order order = baseConfigure.getOrder();
            if (null == order && "DIM_f35d0b803bcb49bcad1bcdae4d89c386".equalsIgnoreCase(baseConfigure.getCode())) {
                order = new Order();
                order.setCode(baseConfigure.getCode());
                order.setSortType(SortType.DESC);
            }

            if (null != order) {

                SortType orderType = order.getSortType();
                if (!SortType.DEFAULT.equals(orderType)) {
                    orderList.add(order);
                }

                String code = order.getCode();
                List<String> values = order.getValueList();
                List<String> codeValues = new LinkedList<>();
                if (isDimension(code) && !CollectionUtils.isEmpty(values)) {
                    Set<String> dimCodeSet = new HashSet();
                    dimCodeSet.add(code);
                    IndicatorTuple tuple = this.indicatorService.getIndicatorTableInfo(dimCodeSet, new HashSet());
                    Set<Dimension> dimensionSet = tuple.getDimensionSet();
                    if (!CollectionUtils.isEmpty(dimensionSet)) {

                        Dimension dim = null;
                        for (Dimension dimension : dimensionSet) {
                            dim = dimension;
                        }

                        DimType dimType = dim.getDimType();
                        if (!DimType.DEGENERATE_DIM.equals(dimType)) {

                            DimensionQueryParam dimQueryParam = new DimensionQueryParam();
                            dimQueryParam.setCode(code);

                            List<Filter> filterList = new LinkedList<>();

                            Filter filter = new Filter();
                            filter.setCode(code);
                            filter.setInternal(true);

                            for (String value : values) {

                                Operator oper1 = new Operator();

                                oper1.setSqlOprType(SqlOprType.LIKE);
                                oper1.setSqlLogicalType(SqlLogicalType.OR);
                                oper1.getDataList().add(value);

                                filter.getOperatorList().add(oper1);

                            }

                            filterList.add(filter);
                            dimQueryParam.setFilterList(filterList);

                            PageData pageData = this.dimQueryService.execQueryDimensionValues(dimQueryParam, false);
                            if (null != pageData && !CollectionUtils.isEmpty(pageData.getCellList())) {
                                List<List<Cell>> cellList = pageData.getCellList();
                                for (String value : values) {
                                    for (List<Cell> cells : cellList) {
                                        for (Cell cell : cells) {
                                            if (value.equalsIgnoreCase(cell.getData())) {
                                                codeValues.add(cell.getId());
                                            }
                                        }
                                    }
                                }
                            }

                        }
                    }

                }

                if (!CollectionUtils.isEmpty(codeValues)) {
                    order.setValueList(codeValues);
                }

            }

        }

        return orderList;

    }

    /**
     * 获取所有数据源下的所有维度
     * @param allConfigList
     * @return
     */
    private List<DimensionConfigure> getDimConfigList(List<BaseConfigure> allConfigList) {

        List<DimensionConfigure> dimConfigList = new LinkedList<>();

        for (BaseConfigure baseConfigure : allConfigList) {

            if (isDimension(baseConfigure)) {
                dimConfigList.add(DimensionConfigure.build(baseConfigure));
            }
            
        }

        return dimConfigList;

    }

    private List<BaseConfigure> getAllConfigureList(List<BaseConfigure> allConfigList, List<BaseConfigure> dimMeasConfigList) {

        for (BaseConfigure baseConfigure : allConfigList) {

            if (this.isMeasure(baseConfigure)) {
                dimMeasConfigList.add(baseConfigure);
            } else if (this.isDimension(baseConfigure)) {
                dimMeasConfigList.add(baseConfigure);
            } else if (this.isMeasureGroup(baseConfigure)) {
                this.getAllConfigureList(baseConfigure.getMeasGroupSet(), dimMeasConfigList);
            }

        }

        return dimMeasConfigList;

    }

    /**
     * 获取轴上所有元素
     * @param allConfigList
     * @param axisType
     * @return
     */
    private List<BaseConfigure> getAxisList(List<BaseConfigure> allConfigList, AxisType axisType) {

        List<BaseConfigure> axisList = new LinkedList<BaseConfigure>();
        for (BaseConfigure baseConfigure : allConfigList) {
            AxisType baseAxisType = baseConfigure.getAxisType();
            //为null时也算列轴
            if (axisType.equals(baseAxisType) || (AxisType.ROW.equals(axisType) && null == baseAxisType)) {
                axisList.add(baseConfigure);
            }
        }

        return axisList;

    }

    public static boolean haveCodeByConfigList(List<BaseConfigure> configureList, String code) {

        for (BaseConfigure baseConfigure : configureList) {
            if (code.equalsIgnoreCase(baseConfigure.getCode())) {
                return true;
            }
        }

        return false;

    }

    public static boolean haveCode(List<MeasureConfigure> configureList, String code) {

        for (BaseConfigure baseConfigure : configureList) {
            if (code.equalsIgnoreCase(baseConfigure.getCode())) {
                return true;
            }
        }

        return false;

    }

    /**
     * 获取所有数据源下的所有指标信息
     * @param allConfigList
     * @return
     */
    private List<MeasureConfigure> getMeasConfigList(List<BaseConfigure> allConfigList) {

        List<MeasureConfigure> measConfigList = new LinkedList<>();

        for (BaseConfigure baseConfigure : allConfigList) {

            if (isMeasure(baseConfigure)) {
                measConfigList.add(MeasureConfigure.build(baseConfigure));

                String exp = baseConfigure.getExpression();
                if (StringUtil.isNotEmpty(exp)) {

                    String regex = "\\[.*?\\]";
                    Pattern pattern = Pattern.compile(regex);
                    Matcher matcher = pattern.matcher(exp);

                    while (matcher.find()) {
                        String match = matcher.group();
                        String code = match.replaceAll("\\[", "").replaceAll("\\]", "");
                        if (code.indexOf("MEAS_") >= 0 && !haveCode(measConfigList, code) && !haveCodeByConfigList(allConfigList, code)) {
                            //指标
                            BaseConfigure config = new BaseConfigure();
                            config.setCode(code);
                            config.setIsHide(true);
                            measConfigList.add(MeasureConfigure.build(config));
                        }
                    }
                }
            }

        }

        return measConfigList;

    }

    /**
     * 判断数据源中是否含有指标操作，排序、筛选。
     * @param baseConfigureList
     * @return
     */
    private boolean hasDimConfigOrder(List<BaseConfigure> baseConfigureList) {

        boolean isHasDimConfigOrder = false;

        for (BaseConfigure baseConfigure : baseConfigureList) {
            //判断是否为指标
            if (this.isDimension(baseConfigure)) {
                //排序
                Order order = baseConfigure.getOrder();
                if (null != order) {
                    if (!CollectionUtils.isEmpty(order.getValueList())) {
                        isHasDimConfigOrder = true;
                        break;
                    }
                }

            }
        }

        return isHasDimConfigOrder;
    }

    /**
     * 判断数据源中是否含有指标操作，排序、筛选。
     * @param dataSource
     * @return
     */
    private boolean hasMeasOpr(DataSource dataSource) {

        boolean isHasMeasOpr = false;

        List<BaseConfigure> baseConfigureList = dataSource.getConfigureList();
        //所有筛选项
        List<Filter> filterList = dataSource.getFilterList();

        if (!CollectionUtils.isEmpty(filterList)) {
            for (Filter filter : filterList) {
                if (isMeasure(filter)) {
                    isHasMeasOpr = true;
                    break;
                }
            }
        }

        for (BaseConfigure baseConfigure : baseConfigureList) {

            //判断是否为指标
            if (isMeasure(baseConfigure)) {

                /**
                 * 同环比设置
                 */
                List<Ratio> ratioList = baseConfigure.getRatioList();
                if (!CollectionUtils.isEmpty(ratioList)) {
                    isHasMeasOpr = true;
                    break;
                }

                //排序
                Order order = baseConfigure.getOrder();
                SortType sortType = null;
                if (null != order) {
                    sortType = order.getSortType();
                }

                //如果指标不为默认，则需要排序指标
                if (!SortType.DEFAULT.equals(sortType) && !Objects.isNull(sortType)) {
                    isHasMeasOpr = true;
                    break;
                }

                //指标code
                String measCode = baseConfigure.getCode();
                boolean hasFilter = this.hasCode(filterList, measCode);
                //判断指标含有过滤项
                if (hasFilter) {
                    isHasMeasOpr = true;
                }

            }

        }

        return isHasMeasOpr;

    }

    /**
     * 判断Config是否为指标
     * @return
     */
    public static boolean isMeasure(Filter filter) {

        boolean isMeasure = false;
        String code = filter.getCode();
        if (StringUtil.isNotEmpty(code)) {
            code = code.toUpperCase();
            isMeasure = code.indexOf(IndicatorConstant.MEASURE_CODE_PREFIX) >= 0;
        }

        return isMeasure;

    }

    /**
     * 判断Config是否为指标
     * @return
     */
    public static boolean isMeasure(BaseConfigure baseConfigure) {

        boolean isMeasure = false;
        String code = baseConfigure.getCode();
        if (StringUtil.isNotEmpty(code)) {
            code = code.toUpperCase();
            isMeasure = code.indexOf(IndicatorConstant.MEASURE_CODE_PREFIX) >= 0;
        }

        return isMeasure;
    }

    /**
     * 判断Config是否为维度
     * @return
     */
    public static boolean isDimension(BaseConfigure baseConfigure) {

        boolean isDim = false;
        String code = baseConfigure.getCode();
        if (StringUtil.isNotEmpty(code)) {
            code = code.toUpperCase();
            isDim = code.indexOf(IndicatorConstant.DIMSENSION_CODE_PREFIX) >= 0;
        }

        return isDim;
    }

    /**
     * 判断是否是维度
     * @param code
     * @return
     */
    public static boolean isDimension(String code) {
        return code.toUpperCase().indexOf(IndicatorConstant.DIMSENSION_CODE_PREFIX) >= 0;
    }

    /**
     * 判断是否是指标
     * @param code
     * @return
     */
    public static boolean isMeasure(String code) {
        return code.toUpperCase().indexOf(IndicatorConstant.MEASURE_CODE_PREFIX) >= 0;
    }

    /**
     * 判断Config是否为指标分组
     * @return
     */
    public static boolean isMeasureGroup(BaseConfigure baseConfigure) {
        String code = baseConfigure.getCode().toUpperCase();
        return code.toUpperCase().indexOf(IndicatorConstant.MEASURE_GROUP_CODE_PREFIX) >= 0;
    }

    private boolean hasCode(List<Filter> filterList, String code) {

        boolean isHas = false;
        if (!CollectionUtils.isEmpty(filterList) && StringUtil.isNotEmpty(code)) {

            for (Filter filter : filterList) {
                //含有指标搜索
                if (code.equalsIgnoreCase(filter.getCode())) {
                    isHas = true;
                    break;
                }

            }

        }

        return isHas;

    }

    private PageData execQuery(QueryParam queryParam) {

        BuildSqlTuple tuple = this.buildIndicatorSqlTuple(queryParam);
        UserThreadLocalUtil.printCost("BuildSqlTuple");

        PageData pageData = this.querySql(tuple, queryParam.getDataSetType());
        UserThreadLocalUtil.printCost("PageData");

        return pageData;

    }

    private Set<String> getMeasCodes(QueryParam queryParam) {

        //用户选择指标的
        Set<String> codeSet = this.getCodes(queryParam.getMeasureConfigureList());
        List<Filter> filterList = queryParam.getFilterList();

        if (!CollectionUtils.isEmpty(filterList)) {
            for (Filter filter : filterList) {

                String code = filter.getCode();
                if (!StringUtil.isEmpty(code) && code.indexOf(IndicatorConstant.MEASURE_CODE_PREFIX) >= 0 && !codeSet.contains(code)) {
                    codeSet.add(code);
                }

            }
        }

        return codeSet;

    }

    private Set<String> getDimShareCodes(QueryParam queryParam) {

        //用户选择指标的
        Set<String> codeSet = this.getCodes(queryParam.getDimensionConfigureList());
        List<Filter> filterList = queryParam.getFilterList();

        if (!CollectionUtils.isEmpty(filterList)) {
            for (Filter filter : filterList) {

                String code = filter.getCode();
                if (!StringUtil.isEmpty(code)
                        && code.indexOf(IndicatorConstant.DIMSENSION_CODE_PREFIX) >= 0
                            && !codeSet.contains(code)
                                 && CollectionUtils.isEmpty(filter.getAuthElementMeasureSet())) {
                    codeSet.add(code);
                }

            }
        }

        return codeSet;

    }

    private Set<String> getDimCodes(QueryParam queryParam) {

        //用户选择指标的
        Set<String> codeSet = this.getCodes(queryParam.getDimensionConfigureList());
        List<Filter> filterList = queryParam.getFilterList();
        
        if (!CollectionUtils.isEmpty(filterList)) {
            for (Filter filter : filterList) {

                String code = filter.getCode();
                if (!StringUtil.isEmpty(code) && code.indexOf(IndicatorConstant.DIMSENSION_CODE_PREFIX) >= 0 && !codeSet.contains(code)) {
                    codeSet.add(code);
                }
                
            }
        }

        return codeSet;

    }

    //判断所有维度是否都含有这张事实表
    private boolean hasTable(Set<Dimension> dimSet, String tableName) {
        boolean allExist = true;
        for (Dimension dim : dimSet) {
            boolean exist = false;
            List<Table> factTableList = dim.getFactTableList();
            for (Table table : factTableList) {

                if (Strings.isNotEmpty(tableName) && tableName.equalsIgnoreCase(table.getTableName())) {
                    exist = true;
                    break;
                }

            }

            if (!exist) {
                allExist = false;
                break;
            }
        }

        return allExist;
    }

    /**
     * 根据复合指标的事实表，判断维度是否可用。
     */
    private boolean isUseAbleExpFactTable(Table factTable, Set<Dimension> dimSet) {

        Set<Measure> hasAllMeasureSet = factTable.getHasAllMeasureSet();
        boolean isUse = true;
        for (Measure measure : hasAllMeasureSet) {

            List<Table> factTableList = measure.getFactTable();

            boolean useMeasAble = false;
            for (Table table : factTableList) {

                if (MeasureType.ORIGIN.equals(measure.getMeasType())) {
                    if (this.hasTable(dimSet, table.getTableName())) {
                        useMeasAble = true;
                        break;
                    }
                } else {
                    useMeasAble = this.isUseAbleExpFactTable(table, dimSet);
                }

            }

            if (!useMeasAble) {
                isUse = false;
                break;
            }

        }

        return isUse;

    }

    private void choiceMeasure(Measure measure, Set<Dimension> dimSet, QueryParam queryParam) {

        List<Table> effectiveFactTableList = new ArrayList<>();
        Set<Table> noTableDimSet = new HashSet<>();
        List<Table> factTableList = measure.getFactTable();
        if (!CollectionUtils.isEmpty(factTableList)) {

            for (Table factTable : factTableList) {
                //派生或衍生指标
                MeasureType measureType = factTable.getMeasureType();
                boolean isExpression = MeasureType.DERIVED.equals(measureType) || MeasureType.EXTENDED.equals(measureType);
                boolean isFilter = !CollectionUtils.isEmpty(factTable.getFilterList());
                boolean isExp = false;
                if (isExpression) {
                    isExp = this.isUseAbleExpFactTable(factTable, dimSet);
                }

                if (isExp || this.hasTable(dimSet, factTable.getTableName())) {

                    effectiveFactTableList.add(factTable);

                    measure.setMeasType(factTable.getMeasureType());
                    factTable.setApplyType(factTable.getMeasureType());
                    measure.setExpression(factTable.getExpression());
                    measure.setExpList(factTable.getExpList());
                    measure.setHasAllMeasureSet(factTable.getHasAllMeasureSet());
                    measure.setHasAllDimensionSet(factTable.getHasAllDimensionSet());
                    //指标信息来源的事实表
                    measure.setUseTempFactTable(factTable);

                    //目前只取一个可用事实表即可。
                    break;

                } else {
                    noTableDimSet.add(factTable);
                }

            }

        }
        //所有可用的事实表
        measure.setFactTable(effectiveFactTableList);

        Table useTempFactTable = measure.getUseTempFactTable();
        if (null != useTempFactTable) {
            Set<Measure> hasAllMeasureSet = useTempFactTable.getHasAllMeasureSet();
            if (!CollectionUtils.isEmpty(hasAllMeasureSet)) {
                for (Measure sonMeas : hasAllMeasureSet) {
                    this.choiceMeasure(sonMeas, dimSet, queryParam);
                }
            }

        }

        //过滤掉不含所选维度的事实表,重新设置指标类型
        this.sqlCheckService.checkFactTable(measure, noTableDimSet, dimSet);
    }
    
    private Set<Dimension> findExtendedMeasFact(Set<Dimension> allDimSet, Measure measure, BuildSqlTuple tuple, IndicatorTuple indicatorTuple) {

        Set<Dimension> dimSet = new HashSet<>();
        List<Table> factTableList = measure.getFactTable();
        if (!CollectionUtils.isEmpty(factTableList)) {
            for (Table factTable : factTableList) {
                MeasureType measureType = factTable.getMeasureType();
                if (MeasureType.EXTENDED.equals(measureType) || MeasureType.DERIVED.equals(measureType)) {
                    dimSet = factTable.getHasAllDimensionSet();

                    if (!CollectionUtils.isEmpty(dimSet)) {
                        allDimSet.addAll(dimSet);
                    }

                    Set<Measure> hasAllMeasureSet = factTable.getHasAllMeasureSet();
                    if (!CollectionUtils.isEmpty(hasAllMeasureSet)) {
                        for (Measure sonMeas : hasAllMeasureSet) {
                            this.addAllDim(allDimSet, sonMeas, tuple, indicatorTuple);
                        }
                    }

                    break;
                }
            }
        }

        return dimSet;

    }

    private void addAllDim(Set<Dimension> allDimSet, Measure measure, BuildSqlTuple tuple, IndicatorTuple indicatorTuple) {

        Set<Dimension> hasAllDimensionSet = this.findExtendedMeasFact(allDimSet, measure, tuple, indicatorTuple);

        //当前指标所拥有的维度的授权过滤条件
        QueryParam queryParam = tuple.getQueryParam();
        Set<Dimension> measDimSet = SpaceServiceImpl.getDimSetBy(measure.getCode(), queryParam.getAuthElementSet(), indicatorTuple);
        if (!CollectionUtils.isEmpty(measDimSet)) {
            allDimSet.addAll(measDimSet);
        }

        Set<Measure> hasAllMeasureSet = measure.getHasAllMeasureSet();
        if (!CollectionUtils.isEmpty(hasAllMeasureSet)) {
            for (Measure sonMeas : hasAllMeasureSet) {
                this.addAllDim(allDimSet, sonMeas, tuple, indicatorTuple);
            }
        }
    }

    /**
     * 选择合适的指标事实表
     * @param indicatorTuple
     */
    private void choiceMeasure(IndicatorTuple indicatorTuple, BuildSqlTuple tuple) {

        QueryParam queryParam = tuple.getQueryParam();
        Set<Measure> measSet = indicatorTuple.getMeasureSet();
//        Set<Dimension> allDimSet = indicatorTuple.getDimensionSet();

//        for (Measure measure : measSet) {
//            this.addAllDim(allDimSet, measure);
//        }

        for (Measure measure : measSet) {
//            Set<Dimension> allDimSet = new HashSet<>(indicatorTuple.getDimensionSet());
            Set<Dimension> allDimSet = this.getAllDimSet(tuple, indicatorTuple);
            this.addAllDim(allDimSet, measure, tuple, indicatorTuple);
            this.choiceMeasure(measure, allDimSet, queryParam);
        }

    }

    private Set<Dimension> getAllDimSet(BuildSqlTuple tuple, IndicatorTuple indicatorTuple) {

        Set<Dimension> allDimSet = new HashSet<>();
        Set<String> shareCodes = this.getDimShareCodes(tuple.getQueryParam());
        Set<Dimension> dimensionSet = indicatorTuple.getDimensionSet();
        for (Dimension dimension : dimensionSet) {

            for (String shareCode : shareCodes) {

                if (shareCode.equalsIgnoreCase(dimension.getCode())) {
                    allDimSet.add(dimension);
                }

            }

        }

        return allDimSet;
    }

    public static void analysis(QueryParam queryParam, Set<String> allDimCodeSet, Set<String> allMeasCodeSet) {

        Set<String> measCodeSet = new HashSet<>();
        DataSource dataSource = queryParam.getDataSource();
        List<BaseConfigure> configureList = dataSource.getConfigureList();
        for (BaseConfigure configure : configureList) {

            String input = configure.getExpression();
            if (StringUtil.isEmpty(input)) {
                continue;
            }

            String regex = "\\[.*?\\]";
            Pattern pattern = Pattern.compile(regex);
            Matcher matcher = pattern.matcher(input);

            while (matcher.find()) {
                String match = matcher.group();
                String code = match.replaceAll("\\[", "").replaceAll("\\]", "");
                if (code.indexOf("MEAS_") >= 0 && !allMeasCodeSet.contains(code)) {
                    //指标
                    allMeasCodeSet.add(code);
                    measCodeSet.add(code);
                } else if (code.indexOf("DIM_") >= 0 && !allDimCodeSet.contains(code)) {
                    //维度
                    allDimCodeSet.add(code);
                }
            }

        }

        //需要将原子指标补充到查询里
        for (String measCode : measCodeSet) {

            BaseConfigure baseConfigure = new BaseConfigure();
            baseConfigure.setCode(measCode);
            dataSource.getConfigureList().add(baseConfigure);

        }

    }

    /**
     * 从指标集合的事实表中提取第一个非空的 DataConnection。
     * 图谱模式下由 GraphIndicatorServiceImpl 将连接信息填入 Table；
     * 非图谱模式下 Table.connection 为 null，此方法返回 null，调用方降级使用默认数据源。
     */
    private static DataConnection extractConnectionFromMeasures(Set<Measure> measures) {
        if (measures == null) return null;
        for (Measure m : measures) {
            if (m.getFactTable() == null) continue;
            for (Table t : m.getFactTable()) {
                if (t.getConnection() != null) return t.getConnection();
            }
        }
        return null;
    }

    @DS("mysql")
    private BuildSqlTuple buildIndicatorSqlTuple(QueryParam queryParam) {
        BuildSqlTuple tuple = new BuildSqlTuple();
        // 设置展示chartBi的
        tuple.setChartShow(queryParam.isChartShow());
        //数据源Id
        tuple.setDataSourceId(queryParam.getDataSourceId());
        tuple.setTaskId(queryParam.getTaskId());

        boolean hasMeasOpr = queryParam.isHasMeasOpr();
        boolean hasDimConfigOrder = queryParam.isHasDimConfigOrder();
        boolean hasFilterTree = queryParam.isHasFilterTree();

        tuple.setDirectQuery(queryParam.isDirectQuery());

        //含有指标操作或维度自定义排序都需要多嵌套一层查询
        boolean multipleNesting = hasMeasOpr || hasDimConfigOrder || hasFilterTree;
        //是否含有操作行
        tuple.setMultipleNesting(multipleNesting);
        tuple.setQueryParam(queryParam);

        //用户选择需要显示的维度
        Set<String> displayDimCodeSet = this.getCodes(queryParam.getDimensionConfigureList());
        tuple.setDisplayDimensionCodeSet(displayDimCodeSet);

        // 用户选择需要显示的指标
        Set<String> displayMeasCodeSet = this.getCodes(queryParam.getMeasureConfigureList());
        tuple.setDisplayMeasureCodeSet(displayMeasCodeSet);

        //用户选择需要的所有维度
        Set<String> allDimCodeSet = this.getDimCodes(queryParam);
        // 用户选择需要的所有指标
        Set<String> allMeasCodeSet = this.getMeasCodes(queryParam);

        analysis(queryParam, allDimCodeSet, allMeasCodeSet);

        //设置模型是否为指标下钻模型,此处需要根据isMeasureDetail 获取指标所依赖的事实表，并返回
        boolean isMeasureDetail = queryParam.isMeasureDetail();
        tuple.setMeasureDetail(isMeasureDetail);

        //设置是否含有treeFilter
        boolean isFilterTree = !CollectionUtils.isEmpty(queryParam.getFilterTreeList());
        tuple.setFilterTree(isFilterTree);

        //指标权限筛选项
        Set<AuthElement> authElementSet = queryParam.getAuthElementSet();
        tuple.setAuthElementSet(authElementSet);

        IndicatorTuple indicatorTuple = indicatorService.getIndicatorTableInfo(allDimCodeSet, allMeasCodeSet);
        boolean hasOffline = indicatorTuple.getMeasureSet() != null &&
                indicatorTuple.getMeasureSet().stream().anyMatch(m -> !m.isOnline());
        if (hasOffline) {
            throw new RuntimeException("指标已下线");
        }
        this.choiceMeasure(indicatorTuple, tuple);
        this.sqlCheckService.checkIndicatorInfo(indicatorTuple, allDimCodeSet, allMeasCodeSet, queryParam);

        //所有维度（可能含有派生维度）
        Set<Dimension> dimSet = this.getDimensionRank(indicatorTuple);
        tuple.setDimensionSet(dimSet);

        //所有指标（可能含有复合、衍生、派生指标）
        Set<Measure> measSet = this.getMeasureRank(indicatorTuple);
        tuple.setMeasureSet(measSet);

        // 从指标事实表中提取数据库连接信息（图谱模式下由 GraphIndicatorServiceImpl 填充）
        DataConnection graphConn = extractConnectionFromMeasures(measSet);
        if (graphConn != null) {
            tuple.setConnection(graphConn);
        }

        //页面选择的维度
        Set<Dimension> choiceDimSet = this.getDimensions(displayDimCodeSet, dimSet);
        this.applyDimInfo(choiceDimSet);
        tuple.setChoiceDimensionSet(choiceDimSet);

        //页面选择的指标
        Set<Measure> choiceMeasSet = this.getMeasures(queryParam.getMeasureConfigureList(), measSet);
        //补充指标相关信息，指标格式
        this.applyMeasureInfo(choiceMeasSet, queryParam);
        tuple.setChoiceMeasureSet(choiceMeasSet);

        //**此处将未授权的指标统一移除
        Set<Measure> authChoiceMeasSet = this.delNoAuthMeasure(choiceMeasSet, authElementSet, queryParam.getUsername());
        tuple.setAuthMeasureSet(authChoiceMeasSet);

        //交叉表设置X轴、Y轴
        Set<BaseConfigure> rowAxisSet = new LinkedHashSet<>(queryParam.getRowAxisList());
        tuple.setRowAxisSet(rowAxisSet);
        
        Set<BaseConfigure> columnAxisSet = new LinkedHashSet<>(queryParam.getColumnAxisList());
        tuple.setColumnAxisSet(columnAxisSet);

        return tuple;

    }

    /**
     * 判断指标是否存在于分类下
     * @param eleCode
     * @param measCode
     * @return
     */
    private boolean hasAuthMeas(String eleCode, String measCode) {
        boolean isHas = false;
        if (eleCode.equalsIgnoreCase(measCode)) {
            isHas = true;
        } else if (this.isNumber(eleCode)){
            isHas = this.indicatorService.belongToCategory(measCode, eleCode);
        }

        return isHas;

    }

    private boolean isNumber(String value) {
        try {
            Long number = Long.valueOf(value);
            return true;
        } catch (Exception ex) {
            return false;
        }

    }

    /**
     * 移除未授权的指标
     * @param choiceMeasSet
     * @param authElementSet
     * @return
     */
    private Set<Measure> delNoAuthMeasure(Set<Measure> choiceMeasSet, Set<AuthElement> authElementSet, String username) {

        Set<Measure> authMeasSet = new LinkedHashSet<>();
        boolean isSuperAdmin = authService.isSuperAdmin(username);

        Set<Measure> delSet = new HashSet<>();
        for (Measure measure : choiceMeasSet) {

            if (isSuperAdmin) {
                authMeasSet.add(measure);
            } else {
                for (AuthElement authElement : authElementSet) {

                    if (this.hasAuthMeas(authElement.getCode(), measure.getCode())) {
                        authMeasSet.add(measure);
                    }

                }
            }

        }
        return authMeasSet;

    }

    private void applyMeasureInfo(List<BaseConfigure> configureList, Measure measure) {

        String measCode = measure.getCode();
        String measAlias = measure.getAlias();

        for (BaseConfigure baseConfigure : configureList) {

            String baseCode = baseConfigure.getCode();
            String baseAlias = baseConfigure.getAlias();

            if (measCode.equalsIgnoreCase(baseCode)) {

                if (null == measAlias && null == baseAlias || (null != measAlias && measAlias.equalsIgnoreCase(baseAlias))) {
                    ValueFormat valueFormat = baseConfigure.getValueFormat();
                    measure.setValueFormat(valueFormat);
//                    measure.setRatioValueType(baseConfigure.getRatioValueType());
                    measure.setOrder(baseConfigure.getOrder());
                }

//                baseConfigure.setRatioColumnType(measure.getRatioColumnType());
//                baseConfigure.setRatioType(measure.getRatioType());
//                baseConfigure.setRatioValueType(measure.getRatioValueType());

            }

        }

    }

    private void applyDimInfo(Set<Dimension> dimSet) {

        for (Dimension dim : dimSet) {

            DimType dimType = dim.getDimType();
            if (DimType.DEGENERATE_DIM.equals(dimType)) {
                continue;
            }

            ViewType viewType = dim.getViewType();
            if (ViewType.DAY.equals(viewType) || ViewType.MONTH.equals(viewType) ||
                    ViewType.WEEK.equals(viewType) || ViewType.SEASON.equals(viewType) ||
                        ViewType.YEAR.equals(viewType)) {

                List<Table> factTableList = dim.getFactTableList();
                for (Table table : factTableList) {

                    ViewType tableViewType = table.getMasterDimensionViewType();
                    String column = table.getFactColumn();

                    if (!viewType.equals(tableViewType)) {

                        if (ViewType.DAY.equals(tableViewType) && column.indexOf("date_format") < 0) {
                            column = "date_format(`" + column + "` , '%Y-%m-%d') ";
                            table.setFactColumn(column);
                        } else {
                            table.setFactColumn(column);
                        }

                    } else {

                        if (ViewType.DAY.equals(viewType) && column.indexOf("date_format") < 0) {
                            column = "date_format(`" + column + "` , '%Y-%m-%d') ";
                            table.setFactColumn(column);
                        } else {
                            table.setFactColumn(column);
                        }

                    }

                }

            }

        }

    }

    private void applyMeasureInfo(Set<Measure> measureSet, QueryParam queryParam) {

        List<BaseConfigure> allConfigureList = queryParam.getAllConfigureList();
//        List<BaseConfigure> rowConfigureList = queryParam.getRowAxisList();
//        List<BaseConfigure> columnConfigureList = queryParam.getColumnAxisList();
        for (Measure measure : measureSet) {

            this.applyMeasureInfo(allConfigureList, measure);
//            this.applyMeasureInfo(rowConfigureList, measure);
//            this.applyMeasureInfo(columnConfigureList, measure);

        }

    }

    private void addDimByMeas(Set<Dimension> allDimSet, Set<Measure> measSet) {

        for (Measure measure : measSet) {

            List<Table> factTableList = measure.getFactTable();

            for (Table table : factTableList) {
                //此处都是派生指标维度
                this.addDimByDim(allDimSet, table.getHasAllDimensionSet(), true);
                this.addDimByMeas(allDimSet, table.getHasAllMeasureSet());
            }
        }
    }

    private void addMeasByMeas(Set<Measure> allMeasSet, Set<Measure> measSet) {

        allMeasSet.addAll(measSet);
        for (Measure measure : measSet) {

            List<Table> factTableList = measure.getFactTable();

            for (Table table : factTableList) {
                this.addMeasByMeas(allMeasSet, table.getHasAllMeasureSet());
            }

        }
    }

    private void addDimByDim(Set<Dimension> allDimSet, Set<Dimension> sonDimSet, boolean isExtended) {

        for (Dimension sonDim : sonDimSet) {

            boolean isExist = false;
            for (Dimension allDim : allDimSet) {

                if (allDim.getCode().equalsIgnoreCase(sonDim.getCode())) {
                    isExist = true;
                    break;
                }

            }

            if (!isExist) {
                allDimSet.add(sonDim);
            }

        }


        if (isExtended) {
            for (Dimension dimension : allDimSet) {

                if (sonDimSet.contains(dimension) ) {
                    dimension.setExtended(true);
                }
            }
        }
    }

    private void addDimByDim(Set<Dimension> allDimSet, Set<Dimension> sonDimSet) {
        this.addDimByDim(allDimSet, sonDimSet, false);
    }

    private Set<Dimension> getDimensionRank(IndicatorTuple indicatorTuple) {

        Set<Dimension> allDimSet = new LinkedHashSet<>();
        this.addDimByDim(allDimSet, indicatorTuple.getDimensionSet());

        Set<Measure> measureSet = indicatorTuple.getMeasureSet();
        this.addDimByMeas(allDimSet, measureSet);

        return allDimSet;

    }

    private Set<Measure> getMeasureRank(IndicatorTuple indicatorTuple) {

        Set<Measure> allMeasSet = new LinkedHashSet<Measure>();
        this.addMeasByMeas(allMeasSet, indicatorTuple.getMeasureSet());
        return allMeasSet;

    }

    private Set<Measure> getMeasures(List<MeasureConfigure> measConfigList, Set<Measure> measSet) {

        Set<Measure> choiceMeasSet = new LinkedHashSet<Measure>();
        for (MeasureConfigure measConfig : measConfigList) {

            for (Measure measure : measSet) {

                String measCode = measConfig.getCode();
                if (measCode.equalsIgnoreCase(measure.getCode())) {

                    Measure copyMeasure = new Measure();
                    BeanUtils.copyProperties(measure, copyMeasure);

                    List<Ratio> ratioList = measConfig.getRatioList();
                    copyMeasure.setRatioList(ratioList);

                    //新增列只有一个同环比
                    RatioColumnType ratioColumnType = measConfig.getRatioColumnType();
                    copyMeasure.setRatioColumnType(ratioColumnType);
                    copyMeasure.setRatioValueType(measConfig.getRatioValueType());
                    copyMeasure.setRatioType(measConfig.getRatioType());

                    ValueFormat valueFormat = measConfig.getValueFormat();
                    copyMeasure.setValueFormat(valueFormat);
                    copyMeasure.setAlias(measConfig.getAlias());

                    if (RatioColumnType.NEW.equals(ratioColumnType)) {
                        if (!CollectionUtils.isEmpty(ratioList)) {

                            for (Ratio ratio : ratioList) {
                                //第一个同环比类型
                                copyMeasure.setRatioType(ratio.getRatioType());

                                break;

                            }

                        }
                    }

                    choiceMeasSet.add(copyMeasure);

                }
            }

        }

        return choiceMeasSet;

    }

    private Set<Measure> getMeasures(Set<String> displayMeasCodeSet, Set<Measure> measSet) {

        Set<Measure> choiceMeasSet = new LinkedHashSet<Measure>();
        for (String measCode : displayMeasCodeSet) {
            for (Measure measure : measSet) {
                if (measCode.equalsIgnoreCase(measure.getCode())) {
                    choiceMeasSet.add(measure);
                }
            }

        }

        return choiceMeasSet;

    }

    private Set<Dimension> getDimensions(Set<String> displayDimCodeSet, Set<Dimension> dimSet) {

        Set<Dimension> choiceDimSet = new LinkedHashSet<Dimension>();
        for (String dimCode : displayDimCodeSet) {

            for (Dimension dimension : dimSet) {
                if (dimCode.equalsIgnoreCase(dimension.getCode())) {
                    choiceDimSet.add(dimension);
                }
            }

        }

        return choiceDimSet;

    }

    private PageData querySql(BuildSqlTuple tuple, DataSetType dataSetType) {

        PageData pageData = new PageData();

        DataQueryService dataQueryService = this.sqlQueryStrategy.getSqlQueryMethod(dataSetType);
        dataQueryService.queryData(tuple, pageData);

        return pageData;

    }

    @Override
    public Map<String, String> test(String code) {

        Map<String, String> errorMap = new HashMap<>();

        List<Measure> measureList = this.indicatorService.listAllMeasure();
        for (Measure measure : measureList) {

            DynamicDataSourceContextHolder.push(JdbcDataSourceType.MYSQL.getDesc());

            if (!"00".equalsIgnoreCase(code)) {
                if (null != code && !code.equalsIgnoreCase(measure.getCode())) {
                    continue;
                }
            }

            String measCode = measure.getCode();
            DataSource dataSource = new DataSource();
            dataSource.setCacheStrategy(CacheStrategy.OVERWRITE);
            dataSource.setSpaceId(null);
            dataSource.setChartType(ChartType.LINE);
            dataSource.setUsername(UserThreadLocalUtil.getUserName());

            List<BaseConfigure> configureList = new LinkedList<>();
            BaseConfigure measureConfigure = new BaseConfigure();
            measureConfigure.setCode(measCode);
            configureList.add(measureConfigure);

            dataSource.setPageable(false);
            dataSource.setConfigureList(configureList);

            //筛选条件 //维度
            Filter filter = new Filter();
            filter.setCode(measCode);

            Operator operator = new Operator();
            operator.setSqlOprType(SqlOprType.GREATER_THAN_OR_EQUAL);
            operator.getDataList().add("10000");

            filter.getOperatorList().add(operator);

            dataSource.getFilterList().add(filter);
            try {
                PageData pageData = this.execQuery(dataSource);
//                Thread.sleep(1000l);
            } catch (Exception ex) {
                ex.printStackTrace();
                try {
                    Thread.sleep(1000l);
                } catch (Exception xx) {
                    xx.printStackTrace();
                }

                errorMap.put(measCode, ex.toString());
            }

        }

        return errorMap;

    }

    @Autowired
    private DataSourceService dataSourceService;

    @Transactional
    public PageData testQuery(Long id) {
        DataSource dataSource = this.dataSourceService.copyDataSource(id);
        dataSource.setFolder(null);
        dataSource.setSpace(null);
        PageData pageData = this.execQuery(dataSource);
        return pageData;
    }

    @Override
    public List<FilterTree> buildFilterTree(FilterTree dimFilter, List<Filter> dsFilters) {

        List<FilterTree> filterTreeList = new LinkedList<>();

        FilterTree filterTree = new FilterTree();
        filterTree.setFilterType(FilterType.CHILDREN);

//        if (!CollectionUtils.isEmpty(dsFilters)) {
//
//            for (Filter dsFilter : dsFilters) {
//
//                FilterTree dsFilterTree = new FilterTree();
//                dsFilterTree.setFilter(dsFilter);
//                dsFilterTree.setFilterType(FilterType.FILTER);
//                dsFilterTree.setSqlLogicalType(SqlLogicalType.AND);
//
//                filterTree.getFilterTreeSet().add(dsFilterTree);
//
//            }
//
//        }

        if (null != dimFilter) {

            Set<String> dimSet = new HashSet<>();
            Set<FilterTree> filterTreeSet = dimFilter.getFilterTreeSet();

            for (FilterTree tree : filterTreeSet) {
                for (FilterTree filterTree1 : tree.getFilterTreeSet()) {
                    dimSet.add(filterTree1.getFilter().getCode());
                }
            }

            filterTree.getFilterTreeSet().add(dimFilter);

            FilterTree isNullFilterChildrenTree = new FilterTree();
            isNullFilterChildrenTree.setFilterType(FilterType.CHILDREN);
            isNullFilterChildrenTree.setSqlLogicalType(SqlLogicalType.OR);

            Set<FilterTree> nullFilterSet = new HashSet<>();
            for (String dimCode : dimSet) {

                FilterTree nullFilterTree = new FilterTree();

                Filter filter = new Filter();
                filter.setCode(dimCode);
                Operator operator = new Operator();
                operator.setSqlLogicalType(SqlLogicalType.AND);
                operator.setSqlOprType(SqlOprType.IS_NULL);
                operator.getDataList().add("null");

                filter.getOperatorList().add(operator);

                nullFilterTree.setFilter(filter);
                nullFilterTree.setFilterType(FilterType.FILTER);
                nullFilterTree.setSqlLogicalType(SqlLogicalType.OR);

                nullFilterTree.setUnionid(UUID.randomUUID().toString());
                nullFilterSet.add(nullFilterTree);

            }

            isNullFilterChildrenTree.setFilterTreeSet(nullFilterSet);

            filterTree.getFilterTreeSet().add(isNullFilterChildrenTree);
        }

        filterTreeList.add(filterTree);

        return filterTreeList;

    }

    @Override
    public FilterTree buildFilter(PageData pageData) {

        FilterTree filterTree = new FilterTree();
        filterTree.setFilterType(FilterType.CHILDREN);

        List<List<Cell>> cellList = pageData.getCellList();
        if (!CollectionUtils.isEmpty(cellList)) {

            for (List<Cell> cells : cellList) {

                FilterTree cellTrees = new FilterTree();
                cellTrees.setFilterType(FilterType.CHILDREN);
                cellTrees.setSqlLogicalType(SqlLogicalType.OR);

                for (Cell cell : cells) {

                    if (CellType.DIMENSION.equals(cell.getType())) {

                        FilterTree cellTree = new FilterTree();
                        cellTree.setFilterType(FilterType.FILTER);
                        cellTree.setSqlLogicalType(SqlLogicalType.AND);

                        Filter filter = new Filter();
                        filter.setCode(cell.getCode());
                        Operator operator = new Operator();
                        operator.setSqlLogicalType(SqlLogicalType.AND);
                        operator.setSqlOprType(SqlOprType.IN);

                        operator.getDataList().add(cell.getId());

                        filter.getOperatorList().add(operator);

                        cellTree.setFilter(filter);

                        cellTrees.getFilterTreeSet().add(cellTree);
                    }

                }

                filterTree.getFilterTreeSet().add(cellTrees);
                
            }

        }

        return filterTree;

    }

    @Override
    public DataSource getRowDataSource(DataSource dataSource) {

        dataSource.setChartType(ChartType.TABLE);
        List<BaseConfigure> configureList = dataSource.getConfigureList();
        List<BaseConfigure> newConfigureList = new LinkedList<>();
        for (BaseConfigure baseConfigure : configureList) {
            if (!AxisType.COLUMN.equals(baseConfigure.getAxisType()) || isMeasure(baseConfigure)) {
                newConfigureList.add(baseConfigure);
            }
        }

        dataSource.setConfigureList(newConfigureList);

        return dataSource;

    }

    @Override
    public DataSource getColumnDataSource(DataSource dataSource) {

        dataSource.setChartType(ChartType.TABLE);
        List<BaseConfigure> configureList = dataSource.getConfigureList();
        List<BaseConfigure> newConfigureList = new LinkedList<>();
        for (BaseConfigure baseConfigure : configureList) {
            if (!AxisType.ROW.equals(baseConfigure.getAxisType()) || isMeasure(baseConfigure)) {
                newConfigureList.add(baseConfigure);
            }
        }

        dataSource.setConfigureList(newConfigureList);

        return dataSource;

    }

    public static boolean checkSqlShow(DataSource dataSource) {
        return  "fixmodel".equalsIgnoreCase(dataSource.getTaskId());
    }

}
