package com.graphinsight.indicator.service;

import com.graphinsight.indicator.constant.IndicatorConstant;
import com.graphinsight.indicator.enums.*;
import com.graphinsight.indicator.model.*;
import com.graphinsight.indicator.service.impl.CountDataQueryServiceImpl;
import com.graphinsight.indicator.service.impl.QueryExecutorService;
import com.graphinsight.indicator.service.impl.SqlExecutorStrategy;
import com.graphinsight.indicator.util.StringUtil;
import org.hibernate.engine.jdbc.internal.FormatStyle;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.util.CollectionUtils;

import javax.annotation.Resource;
import java.util.*;

public abstract class DataQueryService {

    private final Logger Log = LoggerFactory.getLogger(getClass());

    @Resource(name = "buildSql")
    protected BuildSqlService buildSqlService;

//    @Resource(name = "fullSql")
//    protected BuildSqlService buildFullSqlService;

    @Resource
    protected RedisCacheService redisCacheService;

    @Resource
    private SqlExecutorStrategy sqlExecutorStrategy;

    /**
     * 执行sql，查询数据.
     *
     * @param tuple
     * @return
     */
    public abstract PageData queryData(BuildSqlTuple tuple, PageData pageData);

    protected QueryResult baseFileQuery(BuildSqlTuple tuple, PageData pageData) {

        QueryResult result = null;
        DataSourceType sourceType = tuple.getQueryParam().getSourceType();
        result = this.baseFileIndicatorQuery(tuple, pageData);

        return result;

    }

    /**
     * 基础查询的sql,不含分页、count
     *
     * @param tuple
     * @param pageData
     * @return
     */
    public QueryResult baseFileIndicatorQuery(BuildSqlTuple tuple, PageData pageData) {

        Map<String, List<SingleFactTableSqlAgg>> rootTableMap = this.buildSqlService.getBySourceTable(tuple);
        tuple.setRootTableMap(rootTableMap);

        List<String> rootSqls = buildSqlService.buildRootSqls(tuple);
        Log.info("[] rootSqls sql = {}", rootSqls);
        String fullJoinGroupSql = buildSqlService.buildFullJoinGroupSql(tuple);


        String  aggregatorSql = buildSqlService.buildAggregatorSql(tuple);
        Log.info("[] aggregatorSql sql = {}", aggregatorSql);

        /**
         * 需要制作同环比连接
         */
        boolean isOpenRatio = buildSqlService.checkOpenRadioInfo(tuple);
        if (isOpenRatio) {
            String radioSql = buildSqlService.buildRadioSql(tuple, aggregatorSql);
        }

        //增加排序（维度、指标）、rownumber
        String hasMeasOrderSql = buildSqlService.buildHasMeasOprSql(tuple);
        Log.info("[] hasMeasOrderSql sql = {}", hasMeasOrderSql);

        tuple.setFullJoinGroupSql(hasMeasOrderSql);
        tuple.setAggregatorSql(hasMeasOrderSql);
        String baseReviewSql = buildSqlService.buildReViewSQL(tuple);

        Log.info("[] baseReviewSql sql = {}", baseReviewSql);

        tuple.setCountSql(false);

        tuple.setPlatform(ExecutorPlatform.SYNCFILE);
        QueryResult result = this.queryData(tuple);
        pageData.setReviewSql(tuple.getExecuteSql());

        return result;

    }

    public List<List<Cell>> buildCell(QueryResult queryResult) {

        List<Map<String, Object>> valueMap = queryResult.getValueMap();
        String[] names = queryResult.getColumnNames();

        if(CollectionUtils.isEmpty(valueMap) || (null == names && names.length == 0)) {
            return new ArrayList<>();
        }
        //table
        List<List<Cell>> tableList = new ArrayList<List<Cell>>();
        List<Cell> columnCellList = new ArrayList<Cell>();
        for (String name : names) {

            Cell cell = new Cell();
            cell.setCode(name);
            cell.setData(name);

            columnCellList.add(cell);

        }

        tableList.add(columnCellList);

        for (Map<String, Object> rowMap : valueMap) {

            List<Cell> rowCellList = new ArrayList<Cell>();

            for (Map.Entry<String, Object> rowEntry : rowMap.entrySet()) {

                Cell cell = new Cell();
                String key = rowEntry.getKey();
                cell.setCode(key);
                String type = queryResult.getColTypeMap().get(key);
                Object v = rowEntry.getValue();
                if (v == null) {
//                    v = IndicatorConstant.BI_NULL;
                }
                boolean isInt = false;
                try {

                    double vd = Double.valueOf(String.valueOf(v)).doubleValue();
                    if (vd % ((int)vd) == 0 || vd == 0) {
                        Integer vi = (int)vd;
                        cell.setData(vi.toString());
                        isInt = true;
                    }

                } catch (Exception ex) {

                }
                if (!isInt) {
                    String value = String.valueOf(rowEntry.getValue());
                    if (IndicatorConstant.COL_TYPE_DATETIME.equalsIgnoreCase(type)) {
                        value = value.replaceFirst("T", " ");
                    }

                    cell.setData(value);
                }
                rowCellList.add(cell);
            }

            tableList.add(rowCellList);
            
        }
        
        return tableList;
    }

    public PageInfo buildPageInfo(List<List<String>> dataList) {

        PageInfo pageInfo = new PageInfo(10);
        if(CollectionUtils.isEmpty(dataList)) {
            pageInfo.setTotalRows(0);
            return pageInfo;
        }
        //table
        List<List<Cell>> tableList = new ArrayList<List<Cell>>();
        for (List<String> datas : dataList) {

            Integer cnt = Integer.valueOf(datas.get(0));
            pageInfo.setTotalRows(cnt);


        }

        return pageInfo;

    }

    public List<List<Cell>> buildCell(List<List<String>> dataList, Set<Dimension> dimensionSet, Set<Measure> measureSet) {
        return this.buildCell(dataList, dimensionSet, measureSet, null);
    }

    public List<List<Cell>> buildCell(List<List<String>> dataList, Set<Dimension> dimensionSet, Set<Measure> measureSet, BuildSqlTuple tuple) {

        if(CollectionUtils.isEmpty(dataList)) {
            return new ArrayList<>();
        }
        //table
        List<List<Cell>> tableList = new ArrayList<List<Cell>>();
        Set<String> dimCodeSet = new LinkedHashSet<>();
        boolean hasSubtotal = false;
        if (null != tuple) {
            dimCodeSet = tuple.getDimSubtotal();
            hasSubtotal = true;
        }

        for (List<String> datas : dataList) {
            List<Cell> cellList = new ArrayList<Cell>();
            int index = 0;
            for (Dimension dim : dimensionSet) {

                Cell cell = new Cell();
                cell.setCode(dim.getCode());
                cell.setViewType(dim.getViewType());

                if (StringUtil.isNotEmpty(dim.getAlias())) {
                    cell.setName(dim.getAlias());
                } else {
                    cell.setName(dim.getName());
                }
                cell.setDimType(dim.getDimType());
                cell.setType(CellType.DIMENSION);

                //退化维只有一列描述
                if (DimType.DEGENERATE_DIM.equals(dim.getDimType())) {
                    //退化维id、value一致。
                    String value = datas.get(index++);
                    cell.setId(value);
                    cell.setData(value);
                } else {
                    //其它维度同时含有两列
                    String id = datas.get(index++);
                    String value = datas.get(index++);
                    cell.setId(id);
                    cell.setData(String.valueOf(value));
                }
                cellList.add(cell);
            }

            for (Measure measure : measureSet) {

                String value = null;
                if (index >= datas.size()) {
                    value = null;
                } else {
                    value = String.valueOf(datas.get(index++));
                }

                Cell cell = new Cell();
                cell.setCode(measure.getCode());
                if (StringUtil.isNotEmpty(measure.getAlias())) {
                    cell.setName(measure.getAlias());
                } else {
                    cell.setName(measure.getName());
                }
                cell.setType(CellType.MEASURE);
                cell.setData(value);

                cellList.add(cell);
                RatioType ratioType = measure.getRatioType();
                List<Ratio> ratioList = measure.getRatioList();
                if (!CollectionUtils.isEmpty(ratioList) && ratioList.size() == 1) {

                    Cell.Ratio cellRatio = Cell.buildRatio();
                    cellRatio.setRatioType(ratioType.toString());

                    RatioValueType rvType = measure.getRatioValueType();
                    if (RatioValueType.VALUE.equals(rvType)) {
                        cellRatio.setValue(value);
                    } else {
                        cellRatio.setRatio(value);
                    }

                    if (RatioColumnType.IN.equals(measure.getRatioColumnType())) {
                        cellRatio.setValue(datas.get(index++));
                        cellRatio.setRatio(datas.get(index++));
                    }

                    for (Ratio ratio : ratioList) {
                        //取ratioList中的。
                        cellRatio.setRatioType(ratio.getRatioType().toString());
                    }

                    cell.getRatioList().add(cellRatio);

                } else if (!CollectionUtils.isEmpty(ratioList) && ratioList.size() > 1) {

                    for (Ratio ratio : ratioList) {

                        ratioType = ratio.getRatioType();
                        if (RatioType.MONTHONMONTH.equals(ratioType) || RatioType.YEARYEMOM.equals(ratioType)) {

                            Cell.Ratio cellRatio = Cell.buildRatio();
                            cellRatio.setRatioType(ratioType.toString());
                            String beforValue = datas.get(index++);
                            cellRatio.setValue(beforValue);

                            String ratioValue = datas.get(index++);
                            cellRatio.setRatio(ratioValue);

                            cell.getRatioList().add(cellRatio);

                        }
                    }
                }
            }

            //判断是否是不需要的小计合计，来决定是否添加。
            if (hasSubtotal) {

                if (this.hasAdd(dimCodeSet, cellList)) {
                    tableList.add(cellList);
                }

            } else {
                //add row
                tableList.add(cellList);
            }
        }

        return tableList;
    }

    private boolean hasAdd(Set<String> dimCodeSet, List<Cell> cellList) {

        boolean has = true;

        for (Cell cell : cellList) {

            String code = cell.getCode();
            String id = cell.getId();

            CellType cellType = cell.getType();

            if (CellType.DIMENSION.equals(cellType)) {
                if (dimCodeSet.contains(code) && "null".equalsIgnoreCase(id)) {
                    has = true;
                    break;
                }

                if (!dimCodeSet.contains(code) && "null".equalsIgnoreCase(id)) {
                    has = false;
                    break;
                }
            }

        }

        return has;

    }

    /**
     * List类型基础查询
     * @param tuple
     * @param pageData
     * @return
     */
    protected QueryResult baseListQuery(BuildSqlTuple tuple, PageData pageData) {
        QueryResult queryResult = this.baseListIndicatorQuery(tuple, pageData);
        return queryResult;
    }

    /**
     * 只构建Sql语句
     * @param tuple
     * @param pageData
     * @return
     */
    protected void baseBuildSql(BuildSqlTuple tuple, PageData pageData) {
        this.buildSql(tuple, pageData);
    }


    /**
     * 表格类型基础查询
     * @param tuple
     * @param pageData
     * @return
     */
    protected QueryResult baseMeasureDetailListQuery(BuildSqlTuple tuple, PageData pageData) {
        QueryResult queryResult = this.baseMeasureDetailListIndicatorQuery(tuple, pageData);
        return queryResult;
    }

    /**
     * 表格类型基础查询
     * @param tuple
     * @param pageData
     * @return
     */
    protected QueryResult baseMeasureDetailQuery(BuildSqlTuple tuple, PageData pageData) {
        QueryResult queryResult = this.baseMeasureDetailTableIndicatorQuery(tuple, pageData);
        return queryResult;
    }

    /**
     * 表格类型基础查询
     * @param tuple
     * @param pageData
     * @return
     */
    protected QueryResult baseTableQuery(BuildSqlTuple tuple, PageData pageData) {
        QueryResult queryResult = this.baseTableIndicatorQuery(tuple, pageData);
        return queryResult;
    }

    public QueryResult baseListIndicatorQuery(BuildSqlTuple tuple, PageData pageData) {

        Map<String, List<SingleFactTableSqlAgg>> rootTableMap = this.buildSqlService.getBySourceTable(tuple);
        tuple.setRootTableMap(rootTableMap);

        List<String> rootSqls = buildSqlService.buildRootSqls(tuple);
        Log.info("[] rootSqls sql = {}", rootSqls);
        String fullJoinGroupSql = buildSqlService.buildFullJoinGroupSql(tuple);

        //是否需要嵌套循环查询
        boolean isMultipleNesting = tuple.isMultipleNesting();
//            String pageSql = null;
        String aggregatorSql = null;
        String baseReviewSql = null;

        /**
         * 增加对是否含有指标排序进行判断，如果不含有指标排序走内分页逻辑，如果含有指标排序，无法走内分页，
         * 只能在最外层衍生指标计算完毕后再排序、分页，效率相对内分页较低。所以此处保留两种逻辑，优先采用内分页策略。
         */
        if (!isMultipleNesting) {

//                this.buildCountInfo(fullJoinGroupSql, tuple, pageData);

//                pageSql = buildSqlService.buildPageSql(tuple);
//                Log.info("[] pageSql sql = {}", pageSql);
            aggregatorSql = buildSqlService.buildAggregatorSql(tuple);
            Log.info("[] aggregatorSql sql = {}", aggregatorSql);
            baseReviewSql = buildSqlService.buildReViewSQL(tuple);
            Log.info("[] baseReviewSql sql = {}", baseReviewSql);

        } else {

            aggregatorSql = buildSqlService.buildAggregatorSql(tuple);
            Log.info("[] aggregatorSql sql = {}", aggregatorSql);

            /**
             * 需要制作同环比连接
             */
            boolean isOpenRatio = buildSqlService.checkOpenRadioInfo(tuple);
            if (isOpenRatio) {
                String radioSql = buildSqlService.buildRadioSql(tuple, aggregatorSql);
            }

            boolean isJoinFullTable = tuple.isFilterTree();

            if (isJoinFullTable) {

                //此处需要对拥有派生维度、树筛选组合维度进行全表链接查询
                tuple.setAggregatorSql(aggregatorSql);
                //全表链接因为有需要like操作，需要提前关联维度表。
                baseReviewSql = buildSqlService.buildReViewSQL(tuple);
                Log.info("[] baseReviewSql sql = {}", baseReviewSql);

                tuple.setAggregatorSql(baseReviewSql);
                //增加指标筛选、排序（维度、指标）、rownumber
                String hasMeasOrderSql = buildSqlService.buildHasMeasOprSql(tuple);
//                    this.buildCountInfo(hasMeasOrderSql, tuple, pageData);
//                    Log.info("[] hasMeasOrderSql sql = {}", hasMeasOrderSql);

                tuple.setFullJoinGroupSql(hasMeasOrderSql);
//                    String hasMeasOrderPageSql = buildSqlService.buildHasMeasOprPageSql(tuple);
//                    Log.info("[] hasMeasOrderPageSql sql = {}", hasMeasOrderPageSql);

                //因为后续是以reviewSql作为最终执行语句需要设置到其中。
                tuple.setReviewSql(hasMeasOrderSql);

            } else {
                //增加指标筛选、排序（维度、指标）、rownumber
                String hasMeasOrderSql = buildSqlService.buildHasMeasOprSql(tuple);
//                    this.buildCountInfo(hasMeasOrderSql, tuple, pageData);
                Log.info("[] hasMeasOrderSql sql = {}", hasMeasOrderSql);
                //增加分页
                tuple.setFullJoinGroupSql(hasMeasOrderSql);
//                    Log.info("[] hasMeasOrderSql sql = {}", hasMeasOrderSql);
//                    String hasMeasOrderPageSql = buildSqlService.buildHasMeasOprPageSql(tuple);
                tuple.setAggregatorSql(hasMeasOrderSql);
                Log.info("[] hasMeasOrderPageSql sql = {}", hasMeasOrderSql);
                baseReviewSql = buildSqlService.buildReViewSQL(tuple);
                Log.info("[] baseReviewSql sql = {}", baseReviewSql);
            }

        }

        tuple.setCountSql(false);

        QueryResult result = this.queryData(tuple);
        pageData.setReviewSql(tuple.getExecuteSql());

        return result;

    }

    public void buildSql(BuildSqlTuple tuple, PageData pageData) {

        Map<String, List<SingleFactTableSqlAgg>> rootTableMap = this.buildSqlService.getBySourceTable(tuple);
        tuple.setRootTableMap(rootTableMap);

        //是否需要嵌套循环查询
        boolean isMultipleNesting = tuple.isMultipleNesting();
        List<String> rootSqls = buildSqlService.buildRootSqls(tuple);
        Log.info("[] root sql = {}", rootSqls);
        String fullJoinGroupSql = buildSqlService.buildFullJoinGroupSql(tuple);
        Log.info("[] fullJoinGroupSql sql = {}", fullJoinGroupSql);
        String aggregatorSql = buildSqlService.buildAggregatorSql(tuple);
        Log.info("[] aggregatorSql sql = {}", aggregatorSql);

        if (isMultipleNesting) {

            String multipleNestingSql = buildSqlService.buildHasMeasOprSql(tuple);
            tuple.setAggregatorSql(multipleNestingSql);

        }

        String baseReviewSql = buildSqlService.buildReViewSQL(tuple);
        Log.info("[] baseReviewSql sql = {}", baseReviewSql);

        String sql = QueryExecutorService.formatSql(SourceType.MYSQL, baseReviewSql);

        sql = FormatStyle.BASIC.getFormatter().format(sql);

        pageData.setReviewSql(sql);

    }

    public QueryResult baseMeasureDetailListIndicatorQuery(BuildSqlTuple tuple, PageData pageData) {

        Map<String, List<SingleFactTableSqlAgg>> rootTableMap = this.buildSqlService.getBySourceTable(tuple);
        tuple.setRootTableMap(rootTableMap);

        List<String> rootSqls = buildSqlService.buildMeasureDetailRootSqls(tuple);
        Log.info("[] rootSqls sql = {}", rootSqls);

        //只能有一个
        String fullJoinGroupSql = rootSqls.get(0);
        tuple.setReviewSql(fullJoinGroupSql);

        tuple.setCountSql(false);

        QueryResult result = this.queryData(tuple);
        pageData.setReviewSql(tuple.getExecuteSql());

        return result;

    }

    public QueryResult baseMeasureDetailFileIndicatorQuery(BuildSqlTuple tuple, PageData pageData) {

        Map<String, List<SingleFactTableSqlAgg>> rootTableMap = this.buildSqlService.getBySourceTable(tuple);
        tuple.setRootTableMap(rootTableMap);

        List<String> rootSqls = buildSqlService.buildMeasureDetailRootSqls(tuple);
        Log.info("[] rootSqls sql = {}", rootSqls);

        //只能有一个Sql
        String fullJoinGroupSql = rootSqls.get(0);
        tuple.setReviewSql(fullJoinGroupSql);

        tuple.setCountSql(false);

        tuple.setPlatform(ExecutorPlatform.SYNCFILE);
        QueryResult result = this.queryData(tuple);
        pageData.setReviewSql(tuple.getExecuteSql());

        return result;

    }

    public QueryResult baseMeasureDetailTableIndicatorQuery(BuildSqlTuple tuple, PageData pageData) {

        Map<String, List<SingleFactTableSqlAgg>> rootTableMap = this.buildSqlService.getBySourceTable(tuple);
        tuple.setRootTableMap(rootTableMap);

        List<String> rootSqls = buildSqlService.buildMeasureDetailRootSqls(tuple);
        Log.info("[] rootSqls sql = {}", rootSqls);

        //只能有一个
        String aggregatorSql = rootSqls.get(0);
        tuple.setAggregatorSql(aggregatorSql);

        String fullJoinGroupSql = buildSqlService.buildHasMeasDetailOprSql(tuple);

        //是否需要嵌套循环查询
        String pageSql = null;

        this.buildCountInfo(fullJoinGroupSql, tuple, pageData);

        tuple.setFullJoinGroupSql(fullJoinGroupSql);

        pageSql = buildSqlService.buildPageSql(tuple);
        Log.info("[] pageSql sql = {}", pageSql);

        tuple.setReviewSql(pageSql);
        tuple.setCountSql(false);
        tuple.setMeasureDetail(true);

        QueryResult result = this.queryData(tuple);
        pageData.setReviewSql(tuple.getExecuteSql());

        List<Filter> filterList = tuple.getQueryParam().getFilterList();
        List<Filter> authFilterList = new ArrayList<>();
        String message = "";

        boolean messageNull = true;
        if (!CollectionUtils.isEmpty(filterList)) {

            message = "限制的维度字段，存在于明细表中，用户查询明细的时候卡对应的条件；您当前被限制了";
            for (Filter filter : filterList) {

                if (filter.getIsDetail()) {
                    authFilterList.add(filter);
                    String columnName = filter.getDimColumnName();
                    Boolean isNotNull = !StringUtil.isEmpty(columnName);
                    if (isNotNull && columnName.length() > 0) {
                        messageNull = false;
                    }

                    if (isNotNull) {
                        message += " " + filter.getDimColumnName();
                    }

//                    List<Operator> operatorList = filter.getOperatorList();
//                    if (!CollectionUtils.isEmpty(operatorList)) {
//                        for (Operator operator : operatorList) {
//                            List<String> dataList = operator.getDataList();
//                            if (!CollectionUtils.isEmpty(dataList)) {
//                                for (String data : dataList) {
//                                    message += " value:" + data;
//                                }
//                            }
//                        }
//                    }
                }

            }
            if (!messageNull) {
                message += "的行权限，您看到的是部分数据。";
            } else {
                message = null;
            }

            //明细授权信息
            pageData.setAuthDetailMessage(message);
            pageData.setAuthDetailFilters(authFilterList);

        }

        return result;

    }

    public Integer baseCountQuery(BuildSqlTuple tuple, PageData pageData) {

        Map<String, List<SingleFactTableSqlAgg>> rootTableMap = this.buildSqlService.getBySourceTable(tuple);
        tuple.setRootTableMap(rootTableMap);

        List<String> rootSqls = buildSqlService.buildRootSqls(tuple);
        Log.info("[] rootSqls sql = {}", rootSqls);
        String fullJoinGroupSql = buildSqlService.buildFullJoinGroupSql(tuple);

        //是否需要嵌套循环查询
        boolean isMultipleNesting = tuple.isMultipleNesting();
        String aggregatorSql = null;

        /**
         */
        CountInfo countInfo = null;
        if (!isMultipleNesting) {

            countInfo = this.buildCountInfo(fullJoinGroupSql, tuple, pageData);

        } else {

            aggregatorSql = buildSqlService.buildAggregatorSql(tuple);
            Log.info("[] aggregatorSql sql = {}", aggregatorSql);

            /**
             * 需要制作同环比连接
             */
            boolean isOpenRatio = buildSqlService.checkOpenRadioInfo(tuple);
            if (isOpenRatio) {
                String radioSql = buildSqlService.buildRadioSql(tuple, aggregatorSql);
            }

            //增加排序（维度、指标）、rownumber
            String hasMeasOrderSql = buildSqlService.buildHasMeasOprSql(tuple);
            Log.info("[] hasMeasOrderSql sql = {}", hasMeasOrderSql);

            countInfo = this.buildCountInfo(hasMeasOrderSql, tuple, pageData);

        }

        tuple.setReviewSql(countInfo.getCntSql());

        QueryResult queryResult = this.queryData(tuple);
        Integer cnt = CountDataQueryServiceImpl.getCount(queryResult);

        return cnt;
    }

    public QueryResult baseTableIndicatorQuery(BuildSqlTuple tuple, PageData pageData) {

        Map<String, List<SingleFactTableSqlAgg>> rootTableMap = this.buildSqlService.getBySourceTable(tuple);
        tuple.setRootTableMap(rootTableMap);

        List<String> rootSqls = buildSqlService.buildRootSqls(tuple);
        Log.info("[] rootSqls sql = {}", rootSqls);
        String fullJoinGroupSql = buildSqlService.buildFullJoinGroupSql(tuple);

        //是否需要嵌套循环查询
        boolean isMultipleNesting = tuple.isMultipleNesting();
        String pageSql = null;
        String aggregatorSql = null;
        String baseReviewSql = null;
        CountInfo countInfo = null;

        /**
         * 增加对是否含有指标排序、同环比进行判断，如果不含有指标排序走内分页逻辑，如果含有指标排序，无法走内分页，
         * 只能在最外层衍生指标计算完毕后再排序、分页，效率相对内分页较低。所以此处保留两种逻辑，优先采用内分页策略。
         */
        if (!isMultipleNesting) {

            countInfo = this.buildCountInfo(fullJoinGroupSql, tuple, pageData);

            pageSql = buildSqlService.buildPageSql(tuple);
            Log.info("[] pageSql sql = {}", pageSql);
            aggregatorSql = buildSqlService.buildAggregatorSql(tuple);
            Log.info("[] aggregatorSql sql = {}", aggregatorSql);
            baseReviewSql = buildSqlService.buildReViewSQL(tuple);
            Log.info("[] baseReviewSql sql = {}", baseReviewSql);

        } else {

            aggregatorSql = buildSqlService.buildAggregatorSql(tuple);
            Log.info("[] aggregatorSql sql = {}", aggregatorSql);

            /**
             * 需要制作同环比连接
             */
            boolean isOpenRatio = buildSqlService.checkOpenRadioInfo(tuple);
            if (isOpenRatio) {
                String radioSql = buildSqlService.buildRadioSql(tuple, aggregatorSql);
            }

            boolean isJoinFullTable = tuple.isFilterTree();

            if (isJoinFullTable) {

                //此处需要对拥有派生维度、树筛选组合维度进行全表链接查询
                tuple.setAggregatorSql(aggregatorSql);
                //全表链接因为有需要like操作，需要提前关联维度表。
                baseReviewSql = buildSqlService.buildReViewSQL(tuple);
                Log.info("[] baseReviewSql sql = {}", baseReviewSql);

                tuple.setAggregatorSql(baseReviewSql);
                //增加指标筛选、排序（维度、指标）、rownumber
                String hasMeasOrderSql = buildSqlService.buildHasMeasOprSql(tuple);
                countInfo = this.buildCountInfo(hasMeasOrderSql, tuple, pageData);
                Log.info("[] hasMeasOrderSql sql = {}", hasMeasOrderSql);

                tuple.setFullJoinGroupSql(hasMeasOrderSql);
                String hasMeasOrderPageSql = buildSqlService.buildHasMeasOprPageSql(tuple);
                Log.info("[] hasMeasOrderPageSql sql = {}", hasMeasOrderPageSql);

                //因为后续是以reviewSql作为最终执行语句需要设置到其中。
                tuple.setReviewSql(hasMeasOrderPageSql);

            } else {
                //增加指标筛选、排序（维度、指标）、rownumber
                String hasMeasOrderSql = buildSqlService.buildHasMeasOprSql(tuple);
                countInfo = this.buildCountInfo(hasMeasOrderSql, tuple, pageData);
                Log.info("[] hasMeasOrderSql sql = {}", hasMeasOrderSql);
                //增加分页
                tuple.setFullJoinGroupSql(hasMeasOrderSql);
                Log.info("[] hasMeasOrderSql sql = {}", hasMeasOrderSql);
                String hasMeasOrderPageSql = buildSqlService.buildHasMeasOprPageSql(tuple);
                tuple.setAggregatorSql(hasMeasOrderPageSql);
                Log.info("[] hasMeasOrderPageSql sql = {}", hasMeasOrderPageSql);
                baseReviewSql = buildSqlService.buildReViewSQL(tuple);
                Log.info("[] baseReviewSql sql = {}", baseReviewSql);
            }

        }

        tuple.setCountSql(false);

        boolean direcQuery = tuple.isDirectQuery();
        if (direcQuery) {
            tuple.setReviewSql(countInfo.getCntSql());
        }

        QueryResult result = this.queryData(tuple);
        pageData.setReviewSql(tuple.getExecuteSql());

        return result;

    }

    /**
     * 切割分页数据矩阵
     * @param matrix
     * @param tuple
     * @param pageData
     * @return
     */
    protected Matrix paging(Matrix matrix, BuildSqlTuple tuple, PageData pageData) {

        Matrix pageMatrix = new Matrix();
        pageMatrix.setCellSet(matrix.getCellSet());

        QueryParam queryParam = tuple.getQueryParam();

        Integer pageSize = (null != queryParam.getPageSize() ? queryParam.getPageSize() : IndicatorConstant.DEF_PAGE_SIZE);
        Integer pageNo = (null != queryParam.getPageNo() ? queryParam.getPageNo() : 1);

        Integer height = matrix.getHeight();
        Integer columnMaxDeep = matrix.getColumnMaxDeep();

        if ((height - columnMaxDeep) < pageSize) {
            pageMatrix.setHeight(height);
        } else {
            pageMatrix.setHeight(columnMaxDeep + pageSize);
        }

        pageMatrix.setColumnMaxDeep(columnMaxDeep);

        Integer width = matrix.getWidth();
        Integer rowMaxDeep = matrix.getRowMaxDeep();

        pageMatrix.setWidth(width);
        pageMatrix.setRowMaxDeep(rowMaxDeep);

        //设置左上、右上矩阵
        for (int i = 0; i < columnMaxDeep; i++) {
            for (int j = 0; j < width; j++) {

                Matrix.Cell cell = matrix.get(i, j);
                pageMatrix.set(i, j, cell);

            }
        }

        Integer cnt = height - columnMaxDeep;

        PageInfo pageInfo = new PageInfo(pageSize);
        pageInfo.setTotalRows(cnt);
        pageInfo.calc();
        pageInfo.calcRange(pageNo);
        pageData.setPageInfo(pageInfo);

        Integer startRow = pageInfo.getPageStartRow() + columnMaxDeep;
        Integer endRow = pageInfo.getPageEndRow() + columnMaxDeep;

        //分页窗口大小
        Integer windowPageSize = endRow - startRow;
        //原始数据集合最大的游标行
        Integer cursorRowLen = windowPageSize + columnMaxDeep;

        for (int i = columnMaxDeep; i < cursorRowLen; i++) {

            for (int j = 0; j < width; j++) {

                Matrix.Cell cell = matrix.get(startRow, j);
                pageMatrix.set(i, j, cell);

            }
            startRow++;

        }

        return pageMatrix;

    }

    protected CountInfo buildCountInfo(Matrix matrix, BuildSqlTuple tuple, PageData pageData) {

        QueryParam queryParam = tuple.getQueryParam();
        String queryCntKey = queryParam.getQueryCountId();

        Integer height = matrix.getHeight();
        Integer columnMaxDeep = matrix.getColumnMaxDeep();

        Integer cnt = height - columnMaxDeep;//总行数。
        CountInfo countInfo = null;
        if (!StringUtil.isEmpty(queryCntKey)) {
            countInfo = redisCacheService.get(queryCntKey, CountInfo.class);
            if (null == countInfo) {
                countInfo = new CountInfo();
            } else {
                cnt = countInfo.getCount();
                if (null == cnt) {
                    cnt = 10;//容错
                }
            }
        } else {
            queryCntKey = "_key_cnt_" + UUID.randomUUID().toString();
            countInfo = new CountInfo();
        }

        Integer pageSize = (null != queryParam.getPageSize() ? queryParam.getPageSize() : IndicatorConstant.DEF_PAGE_SIZE);
        Integer pageNo = (null != queryParam.getPageNo() ? queryParam.getPageNo() : 1);
        countInfo.setPageSize(pageSize);
        countInfo.setPageNo(pageNo);

        PageInfo pageInfo = new PageInfo(pageSize);
        pageInfo.setTotalRows(cnt);
        pageInfo.calc();
        pageInfo.calcRange(pageNo);
        pageInfo.setQueryCountId(queryCntKey);

        pageData.setPageInfo(pageInfo);

        tuple.setStartPage(pageInfo.getPageStartRow());
        tuple.setEndPage(pageInfo.getPageEndRow());

        countInfo.setPlatform(tuple.getPlatform());
        redisCacheService.put(queryCntKey, countInfo);

        return countInfo;

    }

    private CountInfo buildCountInfo(String fullSql, BuildSqlTuple tuple, PageData pageData) {
        //count sql
        QueryParam queryParam = tuple.getQueryParam();
        String countSql = buildSqlService.buildCountSql(fullSql, tuple);
        String queryCntKey = queryParam.getQueryCountId();

        Integer pageSize = (null != queryParam.getPageSize() ? queryParam.getPageSize() : IndicatorConstant.DEF_PAGE_SIZE);
        Integer pageNo = (null != queryParam.getPageNo() ? queryParam.getPageNo() : 1);

        Integer cnt = pageSize;//默认分页按10条算。
        CountInfo countInfo = null;
        if (!StringUtil.isEmpty(queryCntKey)) {
            countInfo = redisCacheService.get(queryCntKey, CountInfo.class);
            if (null == countInfo) {
                countInfo = new CountInfo();
            } else {
                cnt = countInfo.getCount();
                if (null == cnt) {
                    cnt = 10;//容错
                }
            }
        } else {
            queryCntKey = "_key_cnt_" + UUID.randomUUID().toString();
            countInfo = new CountInfo();
        }

        countInfo.setCntSql(countSql);

        countInfo.setPageSize(pageSize);
        countInfo.setPageNo(pageNo);

        PageInfo pageInfo = new PageInfo(pageSize);
        pageInfo.setTotalRows(cnt);
        pageInfo.calc();
        pageInfo.calcRange(pageNo);
        pageInfo.setQueryCountId(queryCntKey);

        pageData.setPageInfo(pageInfo);

        tuple.setStartPage(pageInfo.getPageStartRow());
        tuple.setEndPage(pageInfo.getPageEndRow());

        countInfo.setPlatform(tuple.getPlatform());
        redisCacheService.put(queryCntKey, countInfo);

        return countInfo;

    }

    protected QueryResult queryData(BuildSqlTuple tuple) {

        //SQl 处理
        String sql = tuple.getReviewSql();
        sql = sql.replaceAll("\\{SCHEMA\\}\\.", "");
        Log.info(sql);

        //本期默认都使用Doris
        ExecutorPlatform platform = ExecutorPlatform.DORIS;
        if (null != tuple.getPlatform()) {
            platform = tuple.getPlatform();
        }

        QueryExecutorService queryExecutorService = sqlExecutorStrategy.getSqlQueryMethod(platform);
        QueryResult queryResult = queryExecutorService.query(tuple);

        return queryResult;

    }

}
