package com.graphinsight.indicator.service.impl;

import com.alibaba.druid.DbType;
import com.alibaba.druid.sql.SQLUtils;
import com.alibaba.druid.sql.ast.SQLExpr;
import com.alibaba.druid.sql.ast.SQLObject;
import com.alibaba.druid.sql.ast.SQLOrderBy;
import com.alibaba.druid.sql.ast.SQLOver;
import com.alibaba.druid.sql.ast.SQLStatement;
import com.alibaba.druid.sql.ast.expr.SQLAggregateExpr;
import com.alibaba.druid.sql.ast.expr.SQLArrayExpr;
import com.alibaba.druid.sql.ast.expr.SQLBetweenExpr;
import com.alibaba.druid.sql.ast.expr.SQLBinaryOpExpr;
import com.alibaba.druid.sql.ast.expr.SQLBinaryOpExprGroup;
import com.alibaba.druid.sql.ast.expr.SQLCaseExpr;
import com.alibaba.druid.sql.ast.expr.SQLCaseStatement;
import com.alibaba.druid.sql.ast.expr.SQLCastExpr;
import com.alibaba.druid.sql.ast.expr.SQLCharExpr;
import com.alibaba.druid.sql.ast.expr.SQLContainsExpr;
import com.alibaba.druid.sql.ast.expr.SQLDateTimeExpr;
import com.alibaba.druid.sql.ast.expr.SQLDbLinkExpr;
import com.alibaba.druid.sql.ast.expr.SQLExtractExpr;
import com.alibaba.druid.sql.ast.expr.SQLFlashbackExpr;
import com.alibaba.druid.sql.ast.expr.SQLIdentifierExpr;
import com.alibaba.druid.sql.ast.expr.SQLInListExpr;
import com.alibaba.druid.sql.ast.expr.SQLInSubQueryExpr;
import com.alibaba.druid.sql.ast.expr.SQLIntegerExpr;
import com.alibaba.druid.sql.ast.expr.SQLListExpr;
import com.alibaba.druid.sql.ast.expr.SQLMatchAgainstExpr;
import com.alibaba.druid.sql.ast.expr.SQLMethodInvokeExpr;
import com.alibaba.druid.sql.ast.expr.SQLNotExpr;
import com.alibaba.druid.sql.ast.expr.SQLPropertyExpr;
import com.alibaba.druid.sql.ast.expr.SQLSizeExpr;
import com.alibaba.druid.sql.ast.expr.SQLTimeExpr;
import com.alibaba.druid.sql.ast.expr.SQLUnaryExpr;
import com.alibaba.druid.sql.ast.expr.SQLValuesExpr;
import com.alibaba.druid.sql.ast.statement.SQLSelect;
import com.alibaba.druid.sql.ast.statement.SQLSelectItem;
import com.alibaba.druid.sql.ast.statement.SQLSelectOrderByItem;
import com.alibaba.druid.sql.dialect.mysql.ast.statement.MySqlSelectQueryBlock;
import com.alibaba.druid.util.JdbcConstants;
import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.TypeReference;
import com.baomidou.mybatisplus.core.toolkit.StringUtils;
import com.graphinsight.indicator.auto.entity.DwColumn;
import com.graphinsight.indicator.constant.IndicatorConstant;
import com.graphinsight.indicator.enums.*;
import com.graphinsight.indicator.exception.IndicatorParamNotValidException;
import com.graphinsight.indicator.model.*;
import com.graphinsight.indicator.model.dto.FactTable;
import com.graphinsight.indicator.service.BuildSqlService;
import com.graphinsight.indicator.service.ChartQueryService;
import com.graphinsight.indicator.service.IndicatorService;
import com.graphinsight.indicator.util.CloneUtils;
import com.graphinsight.indicator.util.MemCacheUtils;
import com.graphinsight.indicator.util.StringUtil;
import lombok.extern.slf4j.Slf4j;
import org.owasp.esapi.ESAPI;
import org.owasp.esapi.codecs.MySQLCodec;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;

import javax.swing.text.View;
import java.math.BigDecimal;
import java.text.SimpleDateFormat;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Service("buildSql")
@Slf4j
public class BuildSqlServiceImpl implements BuildSqlService {

    @Autowired
    private ChartQueryService chartQueryService;

    @Autowired
    private IndicatorService indicatorService;

    @Override
    public List<String> buildMeasureDetailRootSqls(BuildSqlTuple tuple) {
        tuple.setMeasureDetail(true);
        return this.buildRootSqls(tuple);
    }

    @Override
    public List<String> buildRootSqls(BuildSqlTuple tuple) {
        this.buildRootSqlMapList(tuple);
        if (CollectionUtils.isEmpty(tuple.getSingleFactTableQuerySqlMap())) {
            return new LinkedList<>();
        }
        return tuple.getSingleFactTableQuerySqlMap().values().stream().flatMap(c-> c.stream()).collect(Collectors.toList());
    }

    /**
     * 以MapList方式组织数据。
     * @param tuple
     * @return <source,root_sql>
     */
    public void buildRootSqlMapList(BuildSqlTuple tuple) {

        tuple.setSingleFactTableQuerySqlMap(new HashMap<>());
        Map<String, List<SingleFactTableSqlAgg>> rootTableMap = tuple.getRootTableMap();
        for (Map.Entry<String, List<SingleFactTableSqlAgg>> rootTableList : rootTableMap.entrySet()) {

            String source = rootTableList.getKey();
            for (SingleFactTableSqlAgg singleFactTableSqlAgg : rootTableList.getValue()) {

                List<String> sqlList = tuple.getSingleFactTableQuerySqlMap().get(source);

                if (null == sqlList) {
                    sqlList = new ArrayList<String>();
                    tuple.getSingleFactTableQuerySqlMap().put(source, sqlList);
                }

                String rootSql = this.buildRootSql(source, singleFactTableSqlAgg, tuple);

                sqlList.add(rootSql);

            }
        }

    }

    /**
     * ****核心方法，构建sql****
     *
     * 根据rootTable构建sql
     * @param source 数据源
     * @param singleFactTableSqlAgg 数据源上的事实表
     * @return
     */
    public String buildRootSql(String source, SingleFactTableSqlAgg singleFactTableSqlAgg, BuildSqlTuple tuple) {

        String rootJoinSql = this.buildRootSqlByJoin(source, singleFactTableSqlAgg, tuple);
        return rootJoinSql;

    }

    private String buildColumnSql(SingleFactTableSqlAgg singleFactTableSqlAgg, BuildSqlTuple tuple, Measure measure) {

        String columnSql = "";
        if (null == measure) {
            //无超维构建
            columnSql = this.buildColumnSql(singleFactTableSqlAgg, tuple);
        } else {
            Set<Dimension> groupDimSet = singleFactTableSqlAgg.getGroupDimSet();
            List<Dimension> groupDimList = new LinkedList<>();
            groupDimList.addAll(groupDimSet);

            for (int i = 0; i < groupDimList.size(); i++) {
                Dimension dimension = groupDimList.get(i);
                DimType dimType = dimension.getDimType();
                if (DimType.DEGENERATE_DIM.equals(dimType) || true) {

                    String dimFkId = this.getDimFkId(dimension.getCode(), measure, singleFactTableSqlAgg.getTable().getTableName(), tuple.getDimensionSet());
                    dimFkId = setAlias(dimFkId, singleFactTableSqlAgg.getAlias());
                    singleFactTableSqlAgg.getDimColumnList().set(i, dimFkId);
                    singleFactTableSqlAgg.getColumnList().set(i, dimFkId);
                    singleFactTableSqlAgg.getGroupByList().set(i, dimFkId);

                }
            }

            columnSql = this.buildColumnSql(singleFactTableSqlAgg, tuple);

        }

        return columnSql;

    }

    private String buildColumnSql(SingleFactTableSqlAgg singleFactTableSqlAgg, BuildSqlTuple tuple) {

        String columnSql = "";
        List<String> columnList = singleFactTableSqlAgg.getDimColumnList();
        List<String> columnNameList = singleFactTableSqlAgg.getDimColumnNameList();
        List<String> groupByColumnList = singleFactTableSqlAgg.getGroupByList();

        for (int i = 0; i < groupByColumnList.size(); i++) {
            // SELECT 与 GROUP BY 使用完全相同的表达式，满足 only_full_group_by 严格模式
            String column = groupByColumnList.get(i);
            String asName = columnNameList.get(i);
            columnSql += ", " + column + " as " + asName;
        }

        columnSql = columnSql.replaceFirst(",", "");

        return columnSql;

    }

    /**
     * RootSql 核心生成方法，此方法会根据指标数量进行优化
     * @param source
     * @param singleFactTableSqlAgg
     * @param tuple
     * @return
     */
    private String buildRootSqlByJoin(String source, SingleFactTableSqlAgg singleFactTableSqlAgg, BuildSqlTuple tuple) {

        Set<MeasureSonSelectTempTable> measureSonSelectTempTableSet = singleFactTableSqlAgg.getMeasureSonSelectTempTableSet();

        String columnSql = "";
        List<String> columnList = singleFactTableSqlAgg.getDimColumnList();
        List<String> columnNameList = singleFactTableSqlAgg.getDimColumnNameList();
        List<String> groupByColumnList = singleFactTableSqlAgg.getGroupByList();
        //是否含有column
        boolean isNullColumn = (columnList.size() == 0);
        tuple.setNullColumn(isNullColumn);
//        for (int i = 0; i < groupByColumnList.size(); i++) {
//            //root sql中的非派生指标计算
//            String column = groupByColumnList.get(i);
//            String asName = columnNameList.get(i);
//            columnSql += ", (case when " + column + " is null then '" + IndicatorConstant.BI_NULL + "' else " + column + " end) as " + asName;
//        }
//
//        columnSql = columnSql.replaceFirst(",", "");
        columnSql = buildColumnSql(singleFactTableSqlAgg, tuple);

        //只显示指标明细
        boolean isMeasureDetail = tuple.isMeasureDetail();

//        String fromSql = this.getRootFrom(singleFactTableSqlAgg, isMeasureDetail);
//        String whereSql = this.getRootWhere(singleFactTableSqlAgg, tuple);
//        whereSql = whereSql.replaceFirst("where and", "where");
//        whereSql = whereSql.replaceFirst("where or", "where");

        List<String> allColumnList = new LinkedList<>();

        if (!tuple.isAggSql() &&
                !CollectionUtils.isEmpty(singleFactTableSqlAgg.getStatsAllColumnList())) {
            //求聚合函数需要去掉全部信息
            allColumnList.addAll(singleFactTableSqlAgg.getStatsAllColumnList());
        }

        String groupSetByList = this.getGroupingSets(groupByColumnList, allColumnList, tuple);

        //是否只有一个指标
        boolean isOnlyOne = this.hasOnlyOneSelect(measureSonSelectTempTableSet);

        String rootSql = "select ";

        //isMeasureDetail 为 true时，只可能有一个指标
        Set<String> measureCodeSet = tuple.getUseAllMeasCode();
        if (isOnlyOne || isMeasureDetail) {

            /**
             * 只有一个可用指标时，无需制作基表，直接以子表作为查询即可。
             */
            String allMeasColumn = "";
            String nullAlias = "_T" + tuple.getIdx();
            String nullWhere = "";
            for (MeasureSonSelectTempTable sonSelect : measureSonSelectTempTableSet) {

                String measColumn = "";
                if (sonSelect.isFillNull()) {
                    measColumn = "null as " + sonSelect.getAsName();
                    allMeasColumn += ", " + measColumn;
                } else {
                    allMeasColumn += ", " + sonSelect.getColumn();
                    if (measureCodeSet.contains(sonSelect.getMeasure().getCode()) || measureCodeSet.contains(sonSelect.getExMeasCode())) {
                        nullWhere += " or " + nullAlias + "." + sonSelect.getAsName() + " is not null";
                    }
                }

            }

            if (StringUtil.isNotEmpty(nullWhere)) {
                nullWhere = nullWhere.replaceFirst(" or ", " ");
            }

            if (isNullColumn) {
                allMeasColumn = allMeasColumn.replaceFirst(",", "");
            }

            String firstSqlAlis = null;
            //正常指标制作
            for (MeasureSonSelectTempTable measureSonSelectTempTable : measureSonSelectTempTableSet) {

                Measure measure = measureSonSelectTempTable.getMeasure();

//                columnSql = this.buildColumnSql(singleFactTableSqlAgg, tuple, measure);
//                List<String> groupByList = singleFactTableSqlAgg.getGroupByList();
//                groupSetByList = this.getGroupingSets(groupByList, allColumnList, tuple);

                String fromSql = this.getRootFrom(singleFactTableSqlAgg, isMeasureDetail, measure, tuple, false);

                if (measureSonSelectTempTable.isFillNull()) {
                    //如果是补位用的0，不需要生成查询。
                    continue;
                }

                List<MeasureSonSelectWhere> whereList = measureSonSelectTempTable.getWhereList();

                String whereSql = this.getRootWhere(singleFactTableSqlAgg, tuple, measure);
                whereSql = whereSql.replaceFirst("where and", "where");
                whereSql = whereSql.replaceFirst("where or", "where");

                String sonWhereSql = this.buildWhereCondition(whereSql, measureSonSelectTempTable.getWhereCondition());
                List<Filter> filterList = measureSonSelectTempTable.getExFilterList();
                String exMeasureWhere = this.buildExMeasureFilter(filterList, sonWhereSql, singleFactTableSqlAgg, tuple, measure);

                List<Filter> measFilterList = measureSonSelectTempTable.getMeasFilterList();
                String measWhere = this.buildExMeasureFilter(measFilterList, sonWhereSql + exMeasureWhere, singleFactTableSqlAgg, tuple, measure);

                tuple.increment();
                firstSqlAlis = "_T" + tuple.getIdx();

                String groupBySql = " group by " + groupSetByList;
                if (isNullColumn) {
                    groupBySql = "";
                }

                String sql = null;

                if (isMeasureDetail) {
                    //明细取各个表的所有字段
                    columnSql = this.getFromAlias(singleFactTableSqlAgg);
                    sql = "select " + columnSql + " from " + fromSql + " " + sonWhereSql + exMeasureWhere + measWhere;

                } else {
                    sql = "select " + columnSql + allMeasColumn + " from " + fromSql + " " + sonWhereSql + exMeasureWhere + measWhere + groupBySql;
                }

                if (StringUtil.isNotEmpty(nullWhere) && !isMeasureDetail) {
                    sql = "select " + nullAlias + ".* from (" + sql + ") as " + nullAlias + " where " + nullWhere;
                }

                measureSonSelectTempTable.setAsAlias(firstSqlAlis);
                measureSonSelectTempTable.setSonSelectSql(sql);

                rootSql = sql;
                break;

            }

        } else {

            //制作基表,解决全链接问题

            String baseMeasureWhere = "";
            tuple.increment();
            String firstSqlAlis = "_T" + tuple.getIdx();
//            String firstSqlAlis = null;

            String groupBySql = " group by " + groupSetByList;
            if (isNullColumn) {
                groupBySql = "";
            }
            String fromSql = this.getRootFrom(singleFactTableSqlAgg, isMeasureDetail, null, tuple, true);
            String whereSql = this.getRootWhere(singleFactTableSqlAgg, tuple, null);

            whereSql = whereSql.replaceFirst("where and", "where");
            whereSql = whereSql.replaceFirst("where or", "where");

            String baseSql = "(select " + columnSql + " from " + fromSql + " " + whereSql + baseMeasureWhere + groupBySql + ") as " + firstSqlAlis;

            if (isNullColumn) {
                groupBySql = "";
            }

            //别名集合
            Set<String> alisTableSet = new HashSet<>();

            //正常指标制作
            for (MeasureSonSelectTempTable measureSonSelectTempTable : measureSonSelectTempTableSet) {

                if (measureSonSelectTempTable.isFillNull()) {
                    //如果是补位用的0，不需要生成查询。
                    continue;
                }

                Measure measure = measureSonSelectTempTable.getMeasure();

                fromSql = this.getRootFrom(singleFactTableSqlAgg, isMeasureDetail, measure, tuple, false);

                whereSql = this.getRootWhere(singleFactTableSqlAgg, tuple, measure);
                whereSql = whereSql.replaceFirst("where and", "where");
                whereSql = whereSql.replaceFirst("where or", "where");

                List<MeasureSonSelectWhere> whereList = measureSonSelectTempTable.getWhereList();

                String sonWhereSql = this.buildWhereCondition(whereSql, measureSonSelectTempTable.getWhereCondition());
                List<Filter> filterList = measureSonSelectTempTable.getExFilterList();
                String exMeasureWhere = this.buildExMeasureFilter(filterList, sonWhereSql, singleFactTableSqlAgg, tuple, measure);

                List<Filter> measFilterList = measureSonSelectTempTable.getMeasFilterList();
                String measWhere = this.buildExMeasureFilter(measFilterList, sonWhereSql + exMeasureWhere, singleFactTableSqlAgg, tuple, measure);

                tuple.increment();
                String alisTable = "_T" + tuple.getIdx();
                alisTableSet.add(alisTable);
                
                if (null == firstSqlAlis) {
                    firstSqlAlis = alisTable;
                }

                groupBySql = " group by " + groupSetByList;
                String columnMeasureSql = ", " +  measureSonSelectTempTable.getColumn();
                if (isNullColumn) {
                    groupBySql = "";
                    columnMeasureSql = columnMeasureSql.replaceFirst(",", "");
                }

                String sql = "(select " + columnSql + columnMeasureSql + " from " + fromSql + " " + sonWhereSql + exMeasureWhere + measWhere + groupBySql + ") as " + alisTable;
                measureSonSelectTempTable.setAsAlias(alisTable);
                measureSonSelectTempTable.setSonSelectSql(sql);

            }

            String allDimColumn = "";
            for (String columnName : columnNameList) {
                if (alisTableSet.isEmpty()) {
                    // All measures are fillNull (e.g. pagination-only sub-query):
                    // just reference the base table column directly.
                    allDimColumn += ", " + firstSqlAlis + "." + columnName;
                } else {
                    allDimColumn += ", coalesce(";
                    String alisSonTable = "";
                    for (String alis : alisTableSet) {
                        alisSonTable += ", " + alis + "." + columnName;
                    }

                    alisSonTable = alisSonTable.replaceFirst(", ", "");
                    allDimColumn += alisSonTable + ") as " + columnName;
                }

            }

            allDimColumn = allDimColumn.replaceFirst(",", "");
            rootSql += allDimColumn;
            String allMeasColumn = "";
            String fullJoinWhere = "";

            for (MeasureSonSelectTempTable sonSelect : measureSonSelectTempTableSet) {

                String measColumn = null;
                if (sonSelect.isFillNull()) {
                    measColumn = "null as " + sonSelect.getAsName();
                } else {
                    measColumn = sonSelect.getAsAlias() + "." + sonSelect.getAsName();

                    String selfMeasureCode = sonSelect.getMeasure().getCode();
                    String exMeasCode = sonSelect.getExMeasCode();
                    if (null != exMeasCode) {
                        exMeasCode = exMeasCode.replaceFirst("_", "");
                    }

                    if (measureCodeSet.contains(selfMeasureCode) || measureCodeSet.contains(exMeasCode) || !sonSelect.isFillNull()) {
                        fullJoinWhere += " or " + measColumn + " is not null";
                    }

                }

                allMeasColumn += ", " + measColumn;

            }

            if (fullJoinWhere.length() > 0) {
                fullJoinWhere = fullJoinWhere.replaceFirst(" or ", " where (");
                fullJoinWhere += ")";
            }

            if (isNullColumn) {
                allMeasColumn = allMeasColumn.replaceFirst(",", "");
                baseSql = "";
            }

            rootSql += allMeasColumn;
            String joinSonSelect = baseSql;
//            String joinSonSelect = "";

            for (MeasureSonSelectTempTable measureSonSelectTempTable : measureSonSelectTempTableSet) {

                if (!measureSonSelectTempTable.isFillNull()) {

                    String tableAlis = measureSonSelectTempTable.getAsAlias();

                    String joinTypeSql = " left join ";
                    if (isNullColumn) {
                        joinTypeSql = " join ";
                    }

                    boolean isFirst = false;
                    if (StringUtil.isEmpty(joinSonSelect)) {
                        joinTypeSql = "";
                        isFirst = true;//第一个sql无需on条件
                    }

                    joinSonSelect += joinTypeSql + measureSonSelectTempTable.getSonSelectSql();
                    String joinSonSelect123 = QueryExecutorService.formatSql(SourceType.MYSQL, joinSonSelect);
                    if (!isNullColumn && !isFirst) {

                        String onSql = " on ";
                        // on 条件需要关联到所有指标
                        for (String columnName : columnNameList) {
                            onSql += "and " + firstSqlAlis + "." + columnName + "=" + tableAlis + "." + columnName;
                        }

                        onSql = onSql.replaceFirst("on and", "on");
                        joinSonSelect += onSql;

                    }

                }

            }

            if (isNullColumn && joinSonSelect.indexOf(" join ") == 0) {
                joinSonSelect = joinSonSelect.replaceFirst(" join ", "");
            } else if (joinSonSelect.indexOf(" left join ") == 0) {
                joinSonSelect = joinSonSelect.replaceFirst(" left join ", "");
            }

            rootSql += " from " + joinSonSelect + fullJoinWhere;

        }

        String executeSql = QueryExecutorService.formatSql(SourceType.MYSQL, rootSql);

        return rootSql;

    }

    private boolean like(List<String> dataList, String v) {
        boolean exist = false;
        for (String data : dataList) {

            if (v.indexOf(data) >= 0) {
                exist = true;
                break;
            }

        }
        return exist;
    }

    private boolean exist(List<String> dataList, String v) {
        boolean exist = false;
        for (String data : dataList) {

            if (v.equalsIgnoreCase(data)) {
                exist = true;
                break;
            }

        }
        return exist;
    }

    private boolean checkGroupColumn(GroupColumn groupColumn, List<Operator> operatorList) {

        Boolean result = null;

        String value = groupColumn.getName();

        for (Operator operator : operatorList) {

            boolean check = false;
            SqlOprType sqlOprType = operator.getSqlOprType();
            List<String> dataList = operator.getDataList();
            String searchText = dataList.get(0);

            if (SqlOprType.IN.equals(sqlOprType)) {
                check = this.exist(dataList, value);
            } else if (SqlOprType.NOTIN.equals(sqlOprType)) {
                check = !this.exist(dataList, value);
            } else if (SqlOprType.EQUAL.equals(sqlOprType)) {
                check = this.exist(dataList, value);
            } else if (SqlOprType.NOT_EQUAL.equals(sqlOprType)) {
                check = !this.exist(dataList, value);
            } else if (SqlOprType.LIKE.equals(sqlOprType)) {
                check = this.like(dataList, value);
            } else if (SqlOprType.LIKE_NO_INCLUDE.equals(sqlOprType)) {
                check = !this.like(dataList, value);
            } else if (SqlOprType.EQUAL_NULL.equals(sqlOprType)) {
                check = false;
            } else if (SqlOprType.EQUAL_NO_NULL.equals(sqlOprType)) {
                check = true;
            } else if (SqlOprType.EQUAL_NULL_CHART.equals(sqlOprType)) {
                check = false;
            } else if (SqlOprType.EQUAL_NO_NULL_CHART.equals(sqlOprType)) {
                check = true;
            }

            SqlLogicalType sqlLogicalType = operator.getSqlLogicalType();

            if (null == result) {
                result = check;
            } else if (SqlLogicalType.OR.equals(sqlLogicalType)) {
                result = result || check;
            } else if (SqlLogicalType.AND.equals(sqlLogicalType)) {
                result = result && check;
            }
        }

        return result;

    }

    private void getDimOperator(Dimension dimension, Filter filter, List<Filter> realFilterList, Measure measure) {

        Set<Dimension> derivedDimSet = dimension.getHasAllDimensionSet();
        if (!CollectionUtils.isEmpty(derivedDimSet)) {
            //依赖的维度是衍生维度,需要根据衍生维度的筛选项筛选真实基础
            List<GroupColumn> groupColumnList = dimension.getGroupColumnList();
            List<Operator> operatorList = filter.getOperatorList();
            //此处匹配operatorList 与 groupColumnList关系
            for (GroupColumn groupColumn : groupColumnList) {

                //符合operatorList筛选要求的GroupColumn.name.
                if (this.checkGroupColumn(groupColumn, operatorList)) {

                    List<Filter> groupColumnFilterList = groupColumn.getFilterList();
                    for (Filter groupFilter : groupColumnFilterList) {

                        String groupDimCode = groupFilter.getCode();
                        Dimension groupDim = this.findDimension(groupDimCode, derivedDimSet);

                        this.getDimOperator(groupDim, groupFilter, realFilterList, measure);

                    }

                }

            }

        } else {
            //如果是非衍生维度，则需要补充到指标所依赖的维度集合中。
            measure.getHasAllDimensionSet().add(dimension);
            realFilterList.add(filter);
        }

    }

    @Override
    public void pretreatment(Collection<Measure> measures) {
        for (Measure measure : measures) {

            List<Table> factTableList = measure.getFactTable();
            Set<Dimension> hasDimensionSet = new HashSet<>(measure.getHasAllDimensionSet());

            for (Table table : factTableList) {

                hasDimensionSet.addAll(table.getHasAllDimensionSet());

                List<Filter> realFilterList = new LinkedList<Filter>();
                List<Filter> filterList = table.getFilterList();

                for (Filter filter : filterList) {
                    //派生维度code
                    String dimCode = filter.getCode();

                    Dimension dimension = this.findDimension(dimCode, hasDimensionSet);
                    this.getDimOperator(dimension, filter, realFilterList, measure);

                }

                //将所有衍生维度的条件都换成真正的基础维度
                table.setFilterList(realFilterList);
            }

            Set<Measure> hasAllMeasureSet = measure.getHasAllMeasureSet();
            this.pretreatment(hasAllMeasureSet);

        }
    }

    private String buildExMeasureFilter(List<Filter> filterList, String whereSql, SingleFactTableSqlAgg singleFactTableSqlAgg, BuildSqlTuple tuple, Measure measure) {

        String exMeasureWhere = "";

        boolean hasWhere = filterList.size() > 0;

        if (hasWhere) {
            exMeasureWhere += " and (";
        }

        boolean isFirstFilter = true;
        for (Filter filter : filterList) {

            String columnFkId = this.getDimFkId(filter.getCode(), measure, singleFactTableSqlAgg.getTable().getTableName(), tuple.getDimensionSet());
            if (!StringUtil.isEmpty(columnFkId)) {
                filter.setColumn(columnFkId);
            }

            exMeasureWhere += this.buildFilterSql(filter, singleFactTableSqlAgg, tuple, isFirstFilter);
            isFirstFilter = false;
        }

        if (hasWhere) {
            exMeasureWhere += ")";
        }

        if (whereSql.length() == 0 && hasWhere) {
            exMeasureWhere = " where" + exMeasureWhere;
            exMeasureWhere = exMeasureWhere.replaceFirst("where and", "where");
            exMeasureWhere = exMeasureWhere.replaceFirst("where or", "where");
        }

        return exMeasureWhere;

    }

    private String buildExMeasureWhere(List<MeasureSonSelectWhere> whereList, String whereSql) {

        String exMeasureWhere = "";

        boolean hasWhere = whereList.size() > 0;

        if (hasWhere) {
            exMeasureWhere += " and (";
        }

        for (int i = 0; i < whereList.size(); i++) {

            MeasureSonSelectWhere measureSonSelectWhere = whereList.get(i);
            String vSql = this.getValuesSql(measureSonSelectWhere.getValues());

            if (i == 0) {
                //派生维度第一个默认都是 and 逻辑处理
                exMeasureWhere += measureSonSelectWhere.getColume() + " in (" + vSql + ")";
            } else {
                exMeasureWhere += " " + measureSonSelectWhere.getOperator() + " " + measureSonSelectWhere.getColume() + " in (" + vSql + ")";
            }

        }

        if (hasWhere) {
            exMeasureWhere += ")";
        }

        if (whereSql.length() == 0 && hasWhere) {
            exMeasureWhere = " where" + exMeasureWhere;
            exMeasureWhere = exMeasureWhere.replaceFirst("where and", "where");
            exMeasureWhere = exMeasureWhere.replaceFirst("where or", "where");
        }

        return exMeasureWhere;

    }

    private String getValuesSql(String[] values) {

        StringBuilder builder = new StringBuilder();
        for (String v : values) {
            builder.append(",'").append(v).append("'");
        }
        String sql = builder.toString();
        return sql.replaceFirst(",", "");

    }

    private String addWhereCondition(String whereSql, String conditionSql) {

        String conditionWhereSql = "";

        boolean hasWhere = !StringUtil.isEmpty(conditionSql);

        if (hasWhere) {
            conditionWhereSql += " and (" + conditionSql + ")";
        }

        if (whereSql.length() == 0 && hasWhere) {
            conditionWhereSql = " where" + conditionWhereSql;
            conditionWhereSql = conditionWhereSql.replaceFirst("where and", "where");
        }

        return conditionWhereSql;

    }

    private String buildWhereCondition(String whereSql, String conditionSql) {

        String conditionWhereSql = "";

        boolean hasWhere = !StringUtil.isEmpty(conditionSql);

        if (hasWhere) {
            conditionWhereSql += " and (" + conditionSql + ")";
        }

        if (whereSql.length() == 0 && hasWhere) {
            conditionWhereSql = " where" + conditionWhereSql;
            conditionWhereSql = conditionWhereSql.replaceFirst("where and", "where");
            whereSql = conditionWhereSql;
        } else {
            whereSql += conditionWhereSql;
        }

        return whereSql;

    }

    private boolean hasOnlyOneSelect(Set<MeasureSonSelectTempTable> measureSonSelectTempTableSet) {

        int count = 0;
        for (MeasureSonSelectTempTable measureSonSelectTempTable : measureSonSelectTempTableSet) {
            if (!measureSonSelectTempTable.isFillNull()) {
                count++;
                if (count > 1) {
                    break;
                }
            }
        }

        return count == 1;

    }

    private String getGroupingSets(List<String> columnList, List<String> allColumnList, BuildSqlTuple tuple) {

        String groupBySql = " ";

        StringBuilder allItemBuilder = new StringBuilder();

        //正常分组
        String defColumn = this.getGroupSetsItemByAllColumn(null, columnList, tuple);
        allItemBuilder.append(defColumn);

        groupBySql += allItemBuilder.toString();
        return groupBySql;

    }

    private String getGroupSetsItemByAllColumn(String allDimCode, List<String> columnList, BuildSqlTuple tuple) {
        StringBuilder groupByBuilder = new StringBuilder();
        for (String column : columnList) {

            String allColumn = tuple.getDimCodeColumnMap().get(allDimCode);
            if (StringUtil.isNotEmpty(allColumn)) {

                String columnName = column.split("\\.")[1];
                String allColumnName = allColumn.split("\\.")[1];

                if (!columnName.equalsIgnoreCase(allColumnName)) {

                    if (groupByBuilder.length() > 1) {
                        groupByBuilder.append(",");
                    }

                    groupByBuilder.append(column);
                }

            } else {

                if (groupByBuilder.length() > 1) {
                    groupByBuilder.append(",");
                }

                groupByBuilder.append(column);
            }

        }

        return groupByBuilder.toString();
    }

    private static LocalDate strToDate(String strDate) {
        //把字符串转成日期
        Date date = null;
        try {
            date = new SimpleDateFormat("yyyy-MM-dd").parse(strDate);
        } catch (Exception e) {
            e.printStackTrace();
        }

        //返回当前系统默认的时区
        ZoneId zoneId = ZoneId.systemDefault();

        //atZone()方法返回在指定时区,从该Instant生成的ZonedDateTime
        ZonedDateTime zonedDateTime = date.toInstant().atZone(zoneId);
        LocalDate localDate = zonedDateTime.toLocalDate();

        return localDate;

    }

    /**
     * 识别同环比筛选项,并将开始时间减少一个维度级别，保证同环比能正常找到上级。
     * @param tuple
     * @param filter
     * @param operator
     * @return
     */
    private Operator replaceRatioDim(BuildSqlTuple tuple, Filter filter, Operator operator) {

        /**
         * 同环比维度
         */
        Dimension radioDim = tuple.getRadioDim();
        if (null == radioDim) {
            return operator;
        }

        String dimCode = filter.getCode();
        ViewType viewType = radioDim.getViewType();
        if (null != dimCode && dimCode.equalsIgnoreCase(radioDim.getCode())) {

            //同环比类型
            RatioType ratioType = tuple.getRatioType();

            //同环比
            Operator ratioOperator = new Operator();

            DateTimeFormatter dtf = DateTimeFormatter.ofPattern("yyyy-MM-dd");

            SqlOprType sqlOprType = operator.getSqlOprType();
            ratioOperator.setSqlOprType(sqlOprType);
            ratioOperator.setSqlLogicalType(operator.getSqlLogicalType());
            ratioOperator.setEnd(operator.getEnd());

            String begin = operator.getBegin();
            LocalDate currentDate = strToDate(begin);

            //如果是同比，并且类型不为年,日期先统一减少一年
            if (RatioType.YEARYEMOM.equals(ratioType) && !ViewType.YEAR.equals(viewType)) {
                currentDate = currentDate.minusYears(1);
            }

            if (ViewType.DAY.equals(viewType)) {

                LocalDate beginDate = currentDate.minusDays(1);
                begin = beginDate.format(dtf);

            } else if (ViewType.WEEK.equals(viewType)) {

                LocalDate beginDate = currentDate.minusDays(6);
                begin = beginDate.format(dtf);

            } else if (ViewType.MONTH.equals(viewType)) {

                //近1月
                LocalDate beginDate = currentDate.minusMonths(1);
                begin = beginDate.format(dtf);

            } else if (ViewType.SEASON.equals(viewType)) {

                //近3月
                LocalDate beginDate = currentDate.minusMonths(3);
                begin = beginDate.format(dtf);

            } else if (ViewType.YEAR.equals(viewType)) {

                //近一年
                LocalDate beginDate = currentDate.minusYears(1);
                begin = beginDate.format(dtf);

            }

            ratioOperator.setBegin(begin);
            return ratioOperator;

        }

        return operator;

    }

    private String buildFilterSql(Filter filter, BuildSqlTuple tuple, Boolean isFirstFilter) {
        return buildFilterSql(filter, tuple, isFirstFilter, false);
    }
    
    private void setFilterColumn(Filter filter, BuildSqlTuple tuple, boolean isMeasureDetail) {

        if (isMeasureDetail) {

            Dimension dim = getDimension(filter.getCode(), tuple.getDimensionSet());
            DimType dimType = dim.getDimType();

            SingleFactTableSqlAgg factTableSqlAgg = tuple.getRootTableMap().get("Doris").get(0);
            if (DimType.STD_WITH_TABLE.equals(dimType) || DimType.STD_WITHOUT_TABLE.equals(dimType)) {

                Table dimTable = dim.getDimTableList().get(0);

                LeftJoinDimTable leftJoinDimTable = this.findLeftJoinDimTable(factTableSqlAgg, dim, dimTable);
                String orgAlias = leftJoinDimTable.getAlias();

                filter.setColumn(orgAlias + "_" + dimTable.getDimColumn());
                filter.setColumnId(orgAlias + "_" + dimTable.getDimPrimaryKey());

            } else if (DimType.DEGENERATE_DIM.equals(dimType) || DimType.CUSTOM.equals(dimType)) {

                String orgAlias = factTableSqlAgg.getAlias();
                Table factTable = dim.getFactTableList().get(0);

                filter.setColumn(orgAlias + "_" + factTable.getDimColumn());
                filter.setColumnId(orgAlias + "_" + factTable.getDimPrimaryKey());

            }
        }
        
    }

    /**
     * 构建treeFilter筛选
     * @param filter
     * @param tuple
     * @param isFirstFilter
     * @param alias
     * @return
     */
    private String buildFilterSql(Filter filter, BuildSqlTuple tuple, Boolean isFirstFilter, boolean isMeasureDetail) {

        String alias = filter.getAlias();

        this.setFilterColumn(filter, tuple, isMeasureDetail);

        String column = filter.getColumn();
        String whereSql = "";
        List<Operator> operatorList = filter.getOperatorList();

        boolean isFirst = true;
        if (!isFirstFilter) {
            isFirst = false;
        }

        for (Operator operator : operatorList) {

            SqlLogicalType sqlLogicalType = operator.getSqlLogicalType();
            String logicalSql = this.getLogicType(sqlLogicalType, isFirst);
            isFirst = false;

            /**
             * 同环比关联条件时，必须比之前时间范围大一个基本周期，否则最顶端的日期没有同环比。
             */
            operator = this.replaceRatioDim(tuple, filter, operator);

            SqlOprType sqlOprType = operator.getSqlOprType();

            if (SqlOprType.BETEEN.equals(sqlOprType)) {
                whereSql += logicalSql + buildFilterColumn(filter.getAlias(), filter.getColumnId(), filter.getDimType(), filter.getViewType()) + ">= " + formatSqlStringLiteral(operator.getBegin());
                whereSql += " and " + buildFilterColumn(filter.getAlias(), filter.getColumnId(), filter.getDimType(), filter.getViewType()) + "<= " + formatSqlStringLiteral(operator.getEnd());
            } else if (SqlOprType.GREATER_THAN.equals(sqlOprType)) {
                whereSql += logicalSql + buildFilterColumn(filter.getAlias(), filter.getColumnId(), filter.getDimType(), filter.getViewType()) + ">= " + formatSqlStringLiteral(operator.getBegin());
            } else if (SqlOprType.SMALLER_THAN.equals(sqlOprType)) {
                whereSql += logicalSql + buildFilterColumn(filter.getAlias(), filter.getColumnId(), filter.getDimType(), filter.getViewType()) + "<= " + formatSqlStringLiteral(operator.getEnd());
            } else if (SqlOprType.GREATER_THAN_OR_EQUAL.equals(sqlOprType)) {
                whereSql += logicalSql + buildFilterColumn(filter.getAlias(), filter.getColumnId(), filter.getDimType(), filter.getViewType()) + ">= " + formatSqlStringLiteral(operator.getBegin());
            } else if (SqlOprType.SMALLER_THAN_OR_EQUAL.equals(sqlOprType)) {
                whereSql += logicalSql + buildFilterColumn(filter.getAlias(), filter.getColumnId(), filter.getDimType(), filter.getViewType()) + "<= " + formatSqlStringLiteral(operator.getEnd());
            } else if (SqlOprType.IN.equals(sqlOprType)) {

                String values = this.getSqlValue(operator);
                boolean hasNull = this.hasNull(operator);
                String columnId = filter.getColumnId();
                if (hasNull) {
                    whereSql += logicalSql + "(" + buildFilterColumn(filter.getAlias(), columnId, filter.getDimType(), filter.getViewType()) + " in (" + values + ") or ISNULL(" + filter.getAlias() + "." + columnId + "))";
                } else {
                    whereSql += logicalSql + buildFilterColumn(filter.getAlias(), columnId, filter.getDimType(), filter.getViewType()) + " in (" + values + ")";
                }

            } else if (SqlOprType.IS_NULL.equals(sqlOprType)) {

                String values = this.getSqlValue(operator);
                boolean hasNull = this.hasNull(operator);
                String columnId = filter.getColumnId();
                whereSql += logicalSql + buildFilterColumn(filter.getAlias(), columnId, filter.getDimType(), filter.getViewType()) + " is null";

            } else if (SqlOprType.NOTIN.equals(sqlOprType)) {
                String values = this.getSqlValue(operator);
                String columnId = filter.getColumnId();
                whereSql += logicalSql + buildFilterColumn(filter.getAlias(), columnId, filter.getDimType(), filter.getViewType()) + " not in (" + values + ")";
            } else if (SqlOprType.LIKE.equals(sqlOprType)) {
                String value = formatSqlValue(operator.getDataList().get(0));
                whereSql += logicalSql + "CONCAT(" + buildFilterColumn(alias, column, filter.getDimType(), filter.getViewType()) + ", '') like ('%" + value + "%')";
            } else if (SqlOprType.LIKE_NO_INCLUDE.equals(sqlOprType)) {
                String value = formatSqlValue(operator.getDataList().get(0));
                whereSql += logicalSql + "CONCAT(" + buildFilterColumn(alias, alias, filter.getDimType(), filter.getViewType()) + ", '') not like ('%" + value + "%')";
            } else if (SqlOprType.EQUAL_NULL.equals(sqlOprType)) {
                whereSql += logicalSql + buildFilterColumn(alias, column, filter.getDimType(), filter.getViewType()) + " is null";
            } else if (SqlOprType.EQUAL_NO_NULL.equals(sqlOprType)) {
                whereSql += logicalSql + buildFilterColumn(alias, column, filter.getDimType(), filter.getViewType()) + " is not null";
            } else if (SqlOprType.EQUAL_NULL_CHART.equals(sqlOprType)) {
                whereSql += logicalSql + buildFilterColumn(alias, column, filter.getDimType(), filter.getViewType()) + " =''";
            } else if (SqlOprType.EQUAL_NO_NULL_CHART.equals(sqlOprType)) {
                whereSql += logicalSql + buildFilterColumn(alias, column, filter.getDimType(), filter.getViewType()) + " !=''";
            } else if (SqlOprType.EQUAL.equals(sqlOprType)) {
                String value = formatSqlValue(operator.getDataList().get(0));
                whereSql += logicalSql + buildFilterColumn(alias, column, filter.getDimType(), filter.getViewType()) + " ='" + value + "'";
            } else if (SqlOprType.DERIVED_EQUAL_ID.equals(sqlOprType)) {
                String value = formatSqlValue(operator.getDataList().get(0));
                whereSql += logicalSql + buildFilterColumn(filter.getAlias(), filter.getColumn(), filter.getDimType(), filter.getViewType()) + " ='" + value + "'";
            }
        }

        return whereSql;
    }

    public final static String formatSqlValue(String value) {
        return ESAPI.encoder().encodeForSQL(new MySQLCodec(MySQLCodec.Mode.ANSI), value);
    }

    private String buildDeriveDim(LeftJoinDimTable leftJoinDimTable, Filter filter, SingleFactTableSqlAgg singleFactTableSqlAgg, BuildSqlTuple tuple) {

        Dimension dim = leftJoinDimTable.getDim();
        List<GroupColumn> groupColumnList = dim.getGroupColumnList();

        String derivedDimGroupByColumn = "case";
        for (GroupColumn groupColumn : groupColumnList) {

            derivedDimGroupByColumn += " when ";

            List<Filter> filterList = groupColumn.getFilterList();
            String column = "";
            for (Filter dimfilter : filterList) {

                //衍生维度不能设置filter上的code
                if (CollectionUtils.isEmpty(dim.getHasAllDimensionSet())) {
                    dimfilter.setCode(dim.getCode());
                }

                dimfilter.setAlias(leftJoinDimTable.getAlias());
                dimfilter.setColumn(filter.getColumn());
                derivedDimGroupByColumn += this.buildFilterSql(dimfilter, singleFactTableSqlAgg, tuple, true);
            }

            derivedDimGroupByColumn += " then '";
            derivedDimGroupByColumn += groupColumn.getName();
            derivedDimGroupByColumn += "'";
        }

        String dimColumnName = buildFilterColumn(leftJoinDimTable.getAlias(), leftJoinDimTable.getColumnName(), filter.getDimType(), filter.getViewType());
        derivedDimGroupByColumn += " else " + dimColumnName + " end";

        return derivedDimGroupByColumn;

    }

    private boolean hasAliasDimTable(String filterAlias, List<LeftJoinDimTable> leftJoinedDimTableList) {

        boolean has = false;
        for (LeftJoinDimTable leftJoinDimTable : leftJoinedDimTableList) {
            if (null != filterAlias && filterAlias.equalsIgnoreCase(leftJoinDimTable.getAlias())) {
                has = true;
                break;
            }
        }

        return has;

    }

    private String buildFilterSql(Filter filter, SingleFactTableSqlAgg singleFactTableSqlAgg, BuildSqlTuple tuple, Boolean isFirstFilter) {

        String whereSql = "";
        List<Operator> operatorList = filter.getOperatorList();
        String filterAlias = filter.getAlias();

        if (singleFactTableSqlAgg.getLeftJoinedDimTableList().size() == 0) {
            // || !this.hasAliasDimTable(filterAlias, singleFactTableSqlAgg.getLeftJoinedDimTableList())) {
            filterAlias = singleFactTableSqlAgg.getAlias();
        } else {

            boolean hasAlias = false;
            for (LeftJoinDimTable leftJoinDimTable : singleFactTableSqlAgg.getLeftJoinedDimTableList()) {

                if (filterAlias.equalsIgnoreCase(leftJoinDimTable.getAlias())) {

                    if (tuple.isMeasureDetail()) {
                        List<DwColumn> columnList = leftJoinDimTable.getTable().getColumnList();
                        for (DwColumn dwColumn : columnList) {
                            if (dwColumn.getName().equalsIgnoreCase(filter.getColumnId())) {
                                hasAlias = true;
                                break;
                            }
                        }
                    } else {
                        hasAlias = true;
                    }

                    break;
                }
            }

            if (!hasAlias && tuple.isMeasureDetail()) {
                List<DwColumn> columnList = singleFactTableSqlAgg.getTable().getColumnList();
                for (DwColumn dwColumn : columnList) {
                    if (dwColumn.getName().equalsIgnoreCase(filter.getColumnId())) {
                        hasAlias = true;
                        filterAlias = singleFactTableSqlAgg.getAlias();
                        break;
                    }
                }

            }

            if (!hasAlias && filterAlias != singleFactTableSqlAgg.getAlias()) {
                String dimCode = filter.getCode();
                boolean has = false;
                for (LeftJoinDimTable leftJoinDimTable : singleFactTableSqlAgg.getLeftJoinedDimTableList()) {

                    Dimension dim = leftJoinDimTable.getDim();
                    String leftDimCode = null;
                    if (null != dim) {
                        leftDimCode = dim.getCode();
                    }

                    if (null != dimCode && dimCode.equalsIgnoreCase(leftDimCode)) {
                        filterAlias = leftJoinDimTable.getAlias();
                        has = true;
                    }

                }

                if (!has) {
                    filterAlias = singleFactTableSqlAgg.getAlias();
                }

            }

        }

        boolean isFirst = true;
        if (!isFirstFilter) {
            isFirst = false;
        }

        for (Operator operator : operatorList) {

            SqlLogicalType sqlLogicalType = operator.getSqlLogicalType();
            String logicalSql = this.getLogicType(sqlLogicalType, isFirst);
            isFirst = false;

            /**
             * 同环比关联条件时，必须比之前时间范围大一个基本周期，否则最顶端的日期没有同环比。
             */
            operator = this.replaceRatioDim(tuple, filter, operator);

            SqlOprType sqlOprType = operator.getSqlOprType();

            if (SqlOprType.BETEEN.equals(sqlOprType)) {
                whereSql += logicalSql + buildFilterColumn(filterAlias, filter.getColumn(), filter.getDimType(), filter.getViewType()) + ">= " + formatSqlStringLiteral(operator.getBegin());
                whereSql += " and " + buildFilterColumn(filterAlias, filter.getColumn(), filter.getDimType(), filter.getViewType()) + "<= " + formatSqlStringLiteral(operator.getEnd());
            } else if (SqlOprType.GREATER_THAN.equals(sqlOprType)) {
                whereSql += logicalSql + buildFilterColumn(filterAlias, filter.getColumn(), filter.getDimType(), filter.getViewType()) + ">= " + formatSqlStringLiteral(operator.getBegin());
            } else if (SqlOprType.SMALLER_THAN.equals(sqlOprType)) {
                whereSql += logicalSql + buildFilterColumn(filterAlias, filter.getColumn(), filter.getDimType(), filter.getViewType()) + "<= " + formatSqlStringLiteral(operator.getEnd());
            } else if (SqlOprType.GREATER_THAN_OR_EQUAL.equals(sqlOprType)) {
                whereSql += logicalSql + buildFilterColumn(filterAlias, filter.getColumn(), filter.getDimType(), filter.getViewType()) + ">= " + formatSqlStringLiteral(operator.getBegin());
            } else if (SqlOprType.SMALLER_THAN_OR_EQUAL.equals(sqlOprType)) {
                whereSql += logicalSql + buildFilterColumn(filterAlias, filter.getColumn(), filter.getDimType(), filter.getViewType()) + "<= " + formatSqlStringLiteral(operator.getEnd());
            } else if (SqlOprType.IN.equals(sqlOprType)) {

                String values = this.getSqlValue(operator);
                boolean hasNull = this.hasNull(operator);
                String columnId = filter.getColumnId();
                if (hasNull) {
                    whereSql += logicalSql + "(" + buildFilterColumn(filterAlias, columnId, filter.getDimType(), filter.getViewType()) + " in (" + values + ") or ISNULL(" + filter.getAlias() + "." + columnId + "))";
                } else {
                    whereSql += logicalSql + buildFilterColumn(filterAlias, columnId, filter.getDimType(), filter.getViewType()) + " in (" + values + ")";
                }

            } else if (SqlOprType.NOTIN.equals(sqlOprType)) {
                String values = this.getSqlValue(operator);
                String columnId = filter.getColumnId();
                whereSql += logicalSql + buildFilterColumn(filterAlias, columnId, filter.getDimType(), filter.getViewType()) + " not in (" + values + ")";
            } else if (SqlOprType.LIKE.equals(sqlOprType)) {

                LeftJoinDimTable leftJoinDimTable = this.findLeftJoinInfoByWhere(filter, singleFactTableSqlAgg, tuple);
                //维度是否是衍生维度
                Dimension dim = leftJoinDimTable.getDim();
                List<GroupColumn> groupColumnList = null;
                if (null != dim) {
                    groupColumnList = dim.getGroupColumnList();
                }

                if (CollectionUtils.isEmpty(groupColumnList)) {
                    String value = formatSqlValue(operator.getDataList().get(0));
                    whereSql += logicalSql + "CONCAT(" + buildFilterColumn(leftJoinDimTable.getAlias(), leftJoinDimTable.getColumnName(), filter.getDimType(), filter.getViewType()) + ", '') like ('%" + value + "%')";
                } else {

                    String derivedDimGroupByColumn = this.buildDeriveDim(leftJoinDimTable, filter, singleFactTableSqlAgg, tuple);

                    String value = formatSqlValue(operator.getDataList().get(0));
                    whereSql += logicalSql + "CONCAT(" + derivedDimGroupByColumn + ", '') like ('%" + value + "%')";

                }

            } else if (SqlOprType.LIKE_NO_INCLUDE.equals(sqlOprType)) {
                LeftJoinDimTable leftJoinDimTable = this.findLeftJoinInfoByWhere(filter, singleFactTableSqlAgg, tuple);
                //维度是否是衍生维度
                Dimension dim = leftJoinDimTable.getDim();

                List<GroupColumn> groupColumnList = null;
                if (null != dim) {
                    groupColumnList = dim.getGroupColumnList();
                }

                if (CollectionUtils.isEmpty(groupColumnList)) {
                    String value = formatSqlValue(operator.getDataList().get(0));
                    whereSql += logicalSql + "CONCAT(" + buildFilterColumn(leftJoinDimTable.getAlias(), leftJoinDimTable.getColumnName(), filter.getDimType(), filter.getViewType()) + ", '') not like ('%" + value + "%')";
                } else {

                    String derivedDimGroupByColumn = this.buildDeriveDim(leftJoinDimTable, filter, singleFactTableSqlAgg, tuple);
                    String value = formatSqlValue(operator.getDataList().get(0));
                    whereSql += logicalSql + "CONCAT(" + derivedDimGroupByColumn + ", '') not like ('%" + value + "%')";

                }

            } else if (SqlOprType.EQUAL_NULL.equals(sqlOprType)) {
                LeftJoinDimTable leftJoinDimTable = this.findLeftJoinInfoByWhere(filter, singleFactTableSqlAgg, tuple);
                whereSql += logicalSql + buildFilterColumn(leftJoinDimTable.getAlias(), leftJoinDimTable.getColumnName(), filter.getDimType(), filter.getViewType()) + " is null";
            } else if (SqlOprType.EQUAL_NO_NULL.equals(sqlOprType)) {
                LeftJoinDimTable leftJoinDimTable = this.findLeftJoinInfoByWhere(filter, singleFactTableSqlAgg, tuple);
                whereSql += logicalSql + buildFilterColumn(leftJoinDimTable.getAlias(), leftJoinDimTable.getColumnName(), filter.getDimType(), filter.getViewType()) + " is not null";
            } else if (SqlOprType.EQUAL_NULL_CHART.equals(sqlOprType)) {
                LeftJoinDimTable leftJoinDimTable = this.findLeftJoinInfoByWhere(filter, singleFactTableSqlAgg, tuple);
                whereSql += logicalSql + buildFilterColumn(leftJoinDimTable.getAlias(), leftJoinDimTable.getColumnName(), filter.getDimType(), filter.getViewType()) + " =''";
            } else if (SqlOprType.EQUAL_NO_NULL_CHART.equals(sqlOprType)) {
                LeftJoinDimTable leftJoinDimTable = this.findLeftJoinInfoByWhere(filter, singleFactTableSqlAgg, tuple);
                whereSql += logicalSql + buildFilterColumn(leftJoinDimTable.getAlias(), leftJoinDimTable.getColumnName(), filter.getDimType(), filter.getViewType()) + " !=''";
            } else if (SqlOprType.EQUAL.equals(sqlOprType)) {
                LeftJoinDimTable leftJoinDimTable = this.findLeftJoinInfoByWhere(filter, singleFactTableSqlAgg, tuple);
                Dimension dim = leftJoinDimTable.getDim();
                List<GroupColumn> groupColumnList = null;
                if (null != dim) {
                    groupColumnList = dim.getGroupColumnList();
                }

                if (CollectionUtils.isEmpty(groupColumnList)) {
                    String value = formatSqlValue(operator.getDataList().get(0));
                    whereSql += logicalSql + buildFilterColumn(leftJoinDimTable.getAlias(), leftJoinDimTable.getColumnName(), filter.getDimType(), filter.getViewType()) + " ='" + value + "'";
                } else {

                    String derivedDimGroupByColumn = this.buildDeriveDim(leftJoinDimTable, filter, singleFactTableSqlAgg, tuple);

                    String value = formatSqlValue(operator.getDataList().get(0));
                    whereSql += logicalSql + "CONCAT(" + derivedDimGroupByColumn + ", '')" + " ='" + value + "'";
                }
            } else if (SqlOprType.DERIVED_EQUAL_ID.equals(sqlOprType)) {
                String value = formatSqlValue(operator.getDataList().get(0));
                whereSql += logicalSql + buildFilterColumn(filterAlias, filter.getColumn(), filter.getDimType(), filter.getViewType()) + " ='" + value + "'";
            }
        }

        return whereSql;
    }

    private static String buildFilterColumn(String alias, String column, DimType dimType, ViewType viewType) {

//        String filterColumn = alias + "." + column;
        String filterColumn = column.contains(".") ? column : setAlias(column, alias);
//        String conlumAlias = setAlias("sum(supplier_amount) over(partition by car_model_code order by delivery_month)", alias);
        if (DimType.CUSTOM.equals(dimType)) {
            filterColumn = column;
        }

        if (ViewType.DAY.equals(viewType)) {
            filterColumn = "date_format(" + filterColumn + ", '%Y-%m-%d')";
        } else if (ViewType.WEEK.equals(viewType)) {
            filterColumn = "date_format(str_to_date(concat(REPLACE(" + filterColumn + ", '-W', ''), ' 1'), '%Y%u %w'), '%Y-%m-%d')";
        } else if (ViewType.MONTH.equals(viewType)) {
            filterColumn = "concat(" + filterColumn + ", '-01')";
        } else if (ViewType.SEASON.equals(viewType)) {
            filterColumn = "date_format(str_to_date(concat(substr(" + filterColumn + ", 1, 4), '-', ((cast(substr(" + filterColumn + ", 6, 1) as unsigned) - 1) * 3 + 1), '-01'), '%Y-%c-%d'), '%Y-%m-%d')";
        } else if (ViewType.YEAR.equals(viewType)) {
            filterColumn = "concat(" + filterColumn + ", '-01-01')";
        }

        return filterColumn;

    }

    /**
     * 对筛选项进行排序，保证and的条件必须为第一个
     * @param filterList
     * @return
     */
    private List<Filter> orderByLogical(List<Filter> filterList) {

        //去重
        Set<Filter> filterSet = new HashSet();
        for (Filter filter : filterList) {
            filterSet.add(filter);
        }

        LinkedList<Filter> orderFilterList = new LinkedList<>();
        for (Filter filter : filterSet) {

            SqlLogicalType sqlLogicalType = filter.getSqlLogicalType();
            if (SqlLogicalType.AND.equals(sqlLogicalType)) {
                orderFilterList.addFirst(filter);
            } else {
                orderFilterList.addLast(filter);
            }

        }

        return orderFilterList;

    }

    /**
     * 获取指标筛选条件的层次
     * @param whereFilterList
     * @return
     */
    private Set<Long> getHashHierIdSet(List<Filter> whereFilterList) {

        Set<Long> hierSet = new HashSet<>();
        for (Filter filter : whereFilterList) {
            Long hierId = filter.getHierarchyId();
            if (null != hierId) {
                hierSet.add(hierId);
            }
        }
        return hierSet;
    }

    /**
     * 获取root where
     * @param singleFactTableSqlAgg
     * @param tuple
     * @return
     */
    private String getRootWhere(SingleFactTableSqlAgg singleFactTableSqlAgg, BuildSqlTuple tuple, Measure measure) {

        String whereSql = "";
        List<Filter> tempList = singleFactTableSqlAgg.getWhereFilterList();
        List<Filter> whereFilterList = this.orderByLogical(tempList);
        boolean hasColumnDT = false;
        Table table = singleFactTableSqlAgg.getTable();
        if (null != table) {
            hasColumnDT = table.getHasColumnDT();
            if (hasColumnDT) {
                whereSql = "where " + singleFactTableSqlAgg.getAlias() + ".dt=DATE_FORMAT(DATE_SUB(NOW(), INTERVAL 1 DAY), '%Y-%m-%d')";
            }
        }

        /**
         * 如果含有树筛选项则不生成root层过滤。
         */
//        boolean isFilterTree = tuple.isFilterTree();
//        if (isFilterTree) {
//            return whereSql;
//        }

        if (null != whereFilterList && whereFilterList.size() > 0) {

            if (!hasColumnDT) {
                whereSql = "where";
            }

            boolean isFirstFilter = true;
            Set<Long> hierIdSet = this.getHashHierIdSet(whereFilterList);
            for (Long aLong : hierIdSet) {
                whereSql += " and (";

                for (Filter filter : whereFilterList) {

                    Long hierId = filter.getHierarchyId();
                    if (aLong != hierId) {
                        continue;
                    }

                    String columnFkId = this.getDimFkId(filter.getCode(), measure, singleFactTableSqlAgg.getTable().getTableName(), tuple.getDimensionSet());
                    if (!StringUtil.isEmpty(columnFkId)) {
                        filter.setColumn(columnFkId);
                    }

                    whereSql += this.buildFilterSql(filter, singleFactTableSqlAgg, tuple, isFirstFilter);
                    isFirstFilter = false;

                }

                whereSql += ")";
            }

            for (Filter filter : whereFilterList) {

                Dimension radioDim = tuple.getRadioDim();

                if (null != radioDim && radioDim.getCode().equals(filter.getCode()) && null != tuple.getRatioType() && null != tuple.getFixedFilter() && !tuple.getFixedFilter()) {
                    continue;
                }

                Long hierId = filter.getHierarchyId();
                if (null != hierId) {
                    continue;
                }

                SqlLogicalType sqlLogicalType = filter.getSqlLogicalType();
                String opt = "and";
                if (SqlLogicalType.OR.equals(sqlLogicalType)) {
                    opt = "or";
                }

                whereSql += " " + opt + " (";

                String columnFkId = this.getDimFkId(filter.getCode(), measure, singleFactTableSqlAgg.getTable().getTableName(), tuple.getDimensionSet());
                if (!StringUtil.isEmpty(columnFkId)) {
                    filter.setColumn(columnFkId);
                } else {
                    filter.setColumn(filter.getOrgColumn());
                }

                whereSql += this.buildFilterSql(filter, singleFactTableSqlAgg, tuple, true);
                whereSql += ")";

//                isFirstFilter = false;

            }

        }

        if ("where".equalsIgnoreCase(whereSql)) {
            whereSql = "";
        }

        return whereSql;

    }

    /**
     * 在筛选中，维度的筛选，必须是filter中的维度值，不能是关联join中的值。
     * @param filter
     * @return
     */
    private LeftJoinDimTable findLeftJoinInfoByWhere(Filter filter, SingleFactTableSqlAgg singleFactTableSqlAgg, BuildSqlTuple tuple) {

        LeftJoinDimTable leftJoinDimTable = this.findLeftJoinInfo(filter, singleFactTableSqlAgg, tuple);
        String filterDimCode = filter.getCode();
        Dimension filterDim = tuple.findDimension(filterDimCode);
        List<Table> dimTableList = filterDim.getDimTableList();
        if (!CollectionUtils.isEmpty(dimTableList)) {
            for (Table dimTable : dimTableList) {
                leftJoinDimTable.setColumnId(dimTable.getDimPrimaryKey());
                leftJoinDimTable.setColumnName(dimTable.getDimColumn());
            }
        }

        return leftJoinDimTable;

    }


    /**
     * 在关联表中查找筛选的关联表信息
     * @param filter
     * @return
     */
    private LeftJoinDimTable findLeftJoinInfo(Filter filter, SingleFactTableSqlAgg singleFactTableSqlAgg, BuildSqlTuple tuple) {

        LeftJoinDimTable resultInfo = null;
        String alias = singleFactTableSqlAgg.getAlias();
        String filterDimCode = filter.getCode();
        Dimension filterDim = tuple.findDimension(filterDimCode);

        DimType dimType = filterDim.getDimType();

        if (DimType.DEGENERATE_DIM.equals(dimType)) {
            //todo
        } else if (DimType.CUSTOM.equals(dimType)) {

            LeftJoinDimTable custom = new LeftJoinDimTable();
            custom.setAlias(alias);
            custom.setColumnName(filter.getColumn());
            resultInfo = custom;

        } else {
            List<LeftJoinDimTable> leftJoinDimTableList = singleFactTableSqlAgg.getLeftJoinedDimTableList();
            //关联主维度
            for (LeftJoinDimTable leftJoinDimTable : leftJoinDimTableList) {
                String dimCode = null;
                Dimension dimension = leftJoinDimTable.getDim();
                if (null != dimension) {
                    dimCode = dimension.getCode();
                }

                if (null != filterDimCode && null != dimCode && dimCode.equalsIgnoreCase(filterDimCode)) {
                    resultInfo = leftJoinDimTable;
                }

            }

            //次级维度处理
            if (null == resultInfo) {

                for (LeftJoinDimTable leftJoinDimTable : leftJoinDimTableList) {
                    String dimCode = null;
                    Dimension dimension = leftJoinDimTable.getDim();
                    //如果为同层次维度
                    if (this.isHierarchy(dimension, filterDim)) {

                        LeftJoinDimTable dim = new LeftJoinDimTable();
                        dim.setAlias(leftJoinDimTable.getAlias());
//                        Table dimFactTable = this.getDimUseFactTable(dimension, singleFactTableSqlAgg);
                        Table dimFactTable = this.getDimUseDimTable(filterDim, singleFactTableSqlAgg);
                        dim.setColumnId(dimFactTable.getDimPrimaryKey());
                        dim.setColumnName(dimFactTable.getDimColumn());

                        resultInfo = dim;

                    }

                }

            }
        }

        //需要获取的链接信息并不存在时，维度筛选项为退化维
        if (null == resultInfo) {

            Dimension dimension = this.findDimension(filterDimCode, tuple.getDimensionSet());
            LeftJoinDimTable degenerate = new LeftJoinDimTable();
            degenerate.setAlias(alias);

            String columnName = this.getDegeColumnName(dimension, singleFactTableSqlAgg);
            degenerate.setColumnName(columnName);

            resultInfo = degenerate;

        }

        return resultInfo;

    }

    private Table getDimUseDimTable(Dimension dimension, SingleFactTableSqlAgg singleFactTableSqlAgg) {

        Table dimFactTable = null;
        List<Table> dimTableList = dimension.getDimTableList();
        for (Table table : dimTableList) {
            dimFactTable = table;
            break;
        }

        return dimFactTable;

    }

    private Table getDimUseFactTable(Dimension dimension, SingleFactTableSqlAgg singleFactTableSqlAgg) {

        Table dimFactTable = null;
        List<Table> factTableList = dimension.getFactTableList();
        for (Table table : factTableList) {
            if (table.getTableName().equalsIgnoreCase(singleFactTableSqlAgg.getName())) {
                dimFactTable = table;
            }
        }

        return dimFactTable;

    }

    private String getDegeColumnName(Dimension dimension, SingleFactTableSqlAgg singleFactTableSqlAgg) {

        Table dimFactTable = this.getDimUseFactTable(dimension, singleFactTableSqlAgg);
        String columnName = dimFactTable.getFactColumn();

        return columnName;

    }

    private String getSqlOneValue(Operator operator) {
        List<String> valueList = operator.getDataList();
        String measValue = "0";
        for (String value : valueList) {
            String numericValue = value == null ? "" : value.trim();
            try {
                new BigDecimal(numericValue);
            } catch (NumberFormatException ex) {
                throw IndicatorParamNotValidException.error("指标过滤值必须是合法数值");
            }
            measValue = numericValue;
            break;
        }
        return measValue;
    }

    private boolean hasNull(Operator operator) {
        List<String> valueList = operator.getDataList();
        boolean hasNull = false;
        for (String value : valueList) {
            if (StringUtil.isEmpty(value)) {
                hasNull = true;
                break;
            }
        }
        return hasNull;
    }

    private String getSqlValue(Operator operator) {
        List<String> valueList = operator.getDataList();
        StringBuilder values = new StringBuilder();
        for (String value : valueList) {
            if (values.length() > 0) {
                values.append(",");
            }
            values.append(formatSqlStringLiteral(value));
        }
        return values.toString();
    }

    private String formatSqlStringLiteral(String value) {
        return "'" + formatSqlListValue(String.valueOf(value)) + "'";
    }

    private String formatSqlListValue(String value) {
        // ANSI quote doubling is independent of MySQL NO_BACKSLASH_ESCAPES.
        // This remains a compatibility bridge for the legacy SQL builder;
        // new query paths should prefer bound PreparedStatement parameters.
        return ESAPI.encoder().encodeForSQL(new MySQLCodec(MySQLCodec.Mode.ANSI), value);
    }

    private String getTableName(SingleFactTableSqlAgg singleFactTableSqlAgg) {
        return QueryExecutorConfig.QUERY_ENGINE_PREFIX + singleFactTableSqlAgg.getSchema() + "." + singleFactTableSqlAgg.getFrom();
    }

    private String getTableName(LeftJoinDimTable leftJoinedDimTable) {
        return QueryExecutorConfig.QUERY_ENGINE_PREFIX + leftJoinedDimTable.getSchema() + "." + leftJoinedDimTable.getTableName();
    }

    private String getSelectColumn(String alias, Table table) {

//        List<DwColumn> columnList = table.getColumnList();
        StringBuilder columnBuilder = new StringBuilder();
        columnBuilder.append(",").append(alias).append(".").append("*");
//        Set<String> columnSet = new LinkedHashSet<>();
//        for (DwColumn column : columnList) {
//            columnSet.add(column.getName());
//        }
//
//        if (CollectionUtils.isEmpty(columnList)) {
//            columnSet.add(table.getDimPrimaryKey());
//            columnSet.add(table.getDimColumn());
//        }
//
//        for (String column : columnSet) {
//            columnBuilder.append(",").append(alias).append(".").append(column).append(" as ").append(alias).append("_").append(column);
//        }
//
        String selectColumn = columnBuilder.substring(1);

        return selectColumn;

    }

    private String getFromAlias(SingleFactTableSqlAgg singleFactTableSqlAgg) {

        String factColumns = this.getSelectColumn(singleFactTableSqlAgg.getAlias(), singleFactTableSqlAgg.getTable());
        String detailAlias = factColumns;

        List<LeftJoinDimTable> leftJoinedDimTableList = singleFactTableSqlAgg.getLeftJoinedDimTableList();
        for (LeftJoinDimTable leftJoinedDimTable : leftJoinedDimTableList) {
            detailAlias += ", " + this.getSelectColumn(leftJoinedDimTable.getAlias(), leftJoinedDimTable.getTable());
            
        }

        return detailAlias;

    }

    private String getRootFrom(SingleFactTableSqlAgg singleFactTableSqlAgg, boolean isDetail, Measure measure, BuildSqlTuple tuple, Boolean isRootFrom) {
        //此处
        String fromSql = this.getTableName(singleFactTableSqlAgg);
        List<LeftJoinDimTable> leftJoinedDimTableList = singleFactTableSqlAgg.getLeftJoinedDimTableList();
        for (LeftJoinDimTable leftJoinedDimTable : leftJoinedDimTableList) {

//            if (isRootFrom) {
//
//                String leftDimCode = leftJoinedDimTable.getDim().getCode();
//
//                boolean isLeft = false;
//                Set<Dimension> groupDimSet = singleFactTableSqlAgg.getGroupDimSet();
//                if (CollectionUtils.isEmpty(groupDimSet)) {
//
//                    for (Dimension groupDim : groupDimSet) {
//                        if (groupDim.getCode().equalsIgnoreCase(leftDimCode)) {
////                            isLeft = true;
//                            break;
//                        }
//                    }
//                }
//
//                if (!isLeft) {
//                    for (Filter filter : singleFactTableSqlAgg.getWhereFilterList()) {
//                        if (leftDimCode.equalsIgnoreCase(filter.getCode())) {
////                            isLeft = true;
//                        }
//                    }
//                }
//
//                if (!isLeft) {
////                    continue;
//                }
//
//            }

            if (leftJoinedDimTable.isSelfGroupId() && !isDetail) {
                continue;
            }

            Dimension dimension = leftJoinedDimTable.getDim();

            String leftJoinSql = this.getTableName(leftJoinedDimTable);
            if (leftJoinedDimTable.isHasColumnDT()) {

                String dimTableName = this.getTableName(leftJoinedDimTable);
                String lastDaySql = "SELECT MAX(dt) FROM " + dimTableName;
                String columnId = leftJoinedDimTable.getColumnId();
                if (columnId.equalsIgnoreCase(leftJoinedDimTable.getSelfId())) {
                    //主维度
                    columnId = "";
                } else {
                    //次维度
                    columnId = ", " + columnId;
                }
                leftJoinSql = "(select " + leftJoinedDimTable.getSelfId() + columnId + " from " + dimTableName + " where dt=(" + lastDaySql + ") group by " + leftJoinedDimTable.getSelfId() + columnId + ")";
            }

//            String dimColumn = leftJoinedDimTable.getAlias() + "." + leftJoinedDimTable.getSelfId();
            String dimColumn = setAlias(leftJoinedDimTable.getSelfId(), leftJoinedDimTable.getAlias());
            String withoutDimCode = "";

            if (DimType.STD_WITHOUT_TABLE.equals(dimension.getDimType())) {
                String dimColumnCode = leftJoinedDimTable.getAlias() + ".code";
                withoutDimCode = " and " + dimColumnCode + "='" + dimension.getCode() + "'";
            }

            if (StringUtil.isEmpty(withoutDimCode)) {
                String columns = this.getColumns(leftJoinedDimTable, singleFactTableSqlAgg.getDimensionSet());

                if ("dim_pro_sale_dept_df".equalsIgnoreCase(leftJoinedDimTable.getTableName())) {
                    leftJoinSql += " where dt=DATE_FORMAT(DATE_SUB(NOW(), INTERVAL 1 DAY), '%Y-%m-%d') ";
                }

                leftJoinSql = "(select " + columns + " from " + leftJoinSql + " group by " + columns + ")";
            }

            String columnFkId = leftJoinedDimTable.getFkId();
            if (null == measure) {
                if (!CollectionUtils.isEmpty(singleFactTableSqlAgg.getMeasureSet())) {
                    for (Measure useMeasure : singleFactTableSqlAgg.getMeasureSet()) {
                        measure = useMeasure;
                        break;
                    }
                }
            }
            String replaceKey = this.getDimFkId(dimension.getCode(), measure, singleFactTableSqlAgg.getTable().getTableName(), tuple.getDimensionSet());
            if (!StringUtil.isEmpty(replaceKey)) {
                columnFkId = replaceKey;
            }

            String fkId = getColumnName(columnFkId, singleFactTableSqlAgg.getAlias());
            ViewType viewType = dimension.getViewType();
//            String onAliasFkid = singleFactTableSqlAgg.getAlias() + "." + fkId;
            String onAliasFkid = setAlias(fkId, singleFactTableSqlAgg.getAlias());
            if (ViewType.DAY.equals(viewType)) {
                onAliasFkid = "date_format(" + onAliasFkid + ", '%Y-%m-%d')";
            }

            fromSql += " left join " + leftJoinSql + " as #q#" + leftJoinedDimTable.getAlias() + "#q#" +
                    " on " + onAliasFkid + "=" + dimColumn + withoutDimCode;

        }

        return fromSql;

    }

    /**
     * 根据维度code、指标、目标表、维度集合获取指标与维度在表上的超维
     * @param dimCode
     * @param measure
     * @param table
     * @param dimensionSet
     * @return
     */
    private String getDimFkId(String dimCode, Measure measure, String table, Set<Dimension> dimensionSet) {

        String fkId = null;

        if (null == measure) {
            return null;
        }

        List<DimMeasTableColumn> dimMeasTableColumnList = measure.getDimMeasTableColumnList();
        for (DimMeasTableColumn dimMeasTableColumn : dimMeasTableColumnList) {

            Dimension measDim = this.findDimension(dimMeasTableColumn.getDimCode(), dimensionSet);
            Dimension dim = this.findDimension(dimCode, dimensionSet);

            if (dimCode.equalsIgnoreCase(dimMeasTableColumn.getDimCode()) ||
                    (isDateViewType(dim.getViewType()) //日期维度限制，可根据后续交互要求是否包含非日期维度的换维，来判断是否去掉。
                            && null != dim.getLevel() && null != dim.getLevel().getHierarchyCode()
                                && null != measDim.getLevel()
                                    && dim.getLevel().getHierarchyCode().equalsIgnoreCase(measDim.getLevel().getHierarchyCode()))) {

                String tableName = dimMeasTableColumn.getTable();
                if (table.equalsIgnoreCase(tableName)) {
                    fkId = dimMeasTableColumn.getColumn();

                    ViewType viewType = dim.getViewType();
                    if (ViewType.DAY.equals(viewType) || ViewType.MONTH.equals(viewType) ||
                            ViewType.WEEK.equals(viewType) || ViewType.SEASON.equals(viewType) ||
                            ViewType.YEAR.equals(viewType)) {

                            if (fkId.indexOf("date_format") < 0 && fkId.indexOf("CONCAT") < 0) {
                                if (ViewType.YEAR.equals(viewType)) {
                                    fkId = "date_format(`" + fkId + "`, '%Y')";
                                } else if (ViewType.MONTH.equals(viewType)) {
                                    fkId = "date_format(`" + fkId + "`, '%Y-%m')";
                                } else if (ViewType.WEEK.equals(viewType)) {
                                    fkId = "date_format(`" + fkId + "`, '%Y%u')";
                                } else if (ViewType.SEASON.equals(viewType)) {
                                    fkId = "CONCAT(YEAR(`" + fkId + "`), 'Q', QUARTER(`" + fkId + "`))";
                                } else {
                                    // DAY default
                                    fkId = "date_format(`" + fkId + "`, '%Y-%m-%d')";
                                }
                            }

                    }
                    break;
                }
            }
        }

        return fkId;

    }

    private String getColumns(LeftJoinDimTable leftJoinedDimTable, Set<Dimension> dimensionSet) {

        Set<String> columnSet = new HashSet<String>();
        columnSet.add(leftJoinedDimTable.getSelfId());
        columnSet.add(leftJoinedDimTable.getColumnId());
        columnSet.add(leftJoinedDimTable.getColumnName());
//        String columns = leftJoinedDimTable.getColumnId() + ", " + leftJoinedDimTable.getColumnName();
        String alias = leftJoinedDimTable.getAlias();
        for (Dimension dimension : dimensionSet) {

            List<Table> dimTableList = dimension.getDimTableList();
            if (!CollectionUtils.isEmpty(dimTableList)) {
                for (Table table : dimTableList) {

                    if (leftJoinedDimTable.getTableName().equalsIgnoreCase(table.getTableName())) {
                        columnSet.add(table.getDimPrimaryKey());
                        columnSet.add(table.getDimColumn());
                        // 若 dimColumnExpr 引用了额外列（如 type_id），也加入 SELECT/GROUP BY
                        String dimColumnExpr = table.getDimColumnExpr();
                        if (dimColumnExpr != null && !dimColumnExpr.isEmpty()) {
                            // 提取 {d}.colName 中的列名
                            java.util.regex.Matcher m = java.util.regex.Pattern
                                    .compile("\\{d\\}\\.([a-zA-Z_][a-zA-Z0-9_]*)")
                                    .matcher(dimColumnExpr);
                            while (m.find()) {
                                columnSet.add(m.group(1));
                            }
                        }
                    }

                }
            }

        }

        String columns = "";

        for (String col : columnSet) {
            columns += "," + col;
        }

        columns = columns.replaceFirst(",", "");

        return columns;

    }

    /**
     * 将指标下的所有指标增加到维度集合
     * @param useDimCodeSet
     * @param measure
     */
    private void addAllDim(Set<String> useDimCodeSet, Measure measure) {
        Set<Dimension> measDimSet = measure.getHasAllDimensionSet();

        for (Dimension measDim : measDimSet) {
            useDimCodeSet.add(measDim.getCode());
        }

        Set<Measure> hasAllMeasureSet = measure.getHasAllMeasureSet();
        for (Measure sonMeasure : hasAllMeasureSet) {
            this.addAllDim(useDimCodeSet, sonMeasure);
        }

    }

    /**
     * 将指标下的所有指标增加到维度集合
     * @param tuple
     * @param measure
     */
    private void addAllDim(BuildSqlTuple tuple, Measure measure) {
        Set<Dimension> measDimSet = measure.getHasAllDimensionSet();
        Set<Dimension> existDimSet = tuple.getDimensionSet();

        for (Dimension measDim : measDimSet) {

            if (!existDimSet.contains(measDim)) {
                measDim.setAll(true);
                existDimSet.add(measDim);
            }
            
        }

        Set<Measure> hasAllMeasureSet = measure.getHasAllMeasureSet();
        for (Measure sonMeasure : hasAllMeasureSet) {
            this.addAllDim(tuple, sonMeasure);
        }

    }

    @Override
    public void buildRootTable(Measure measure, Map<String, List<SingleFactTableSqlAgg>> sourceTableMap, BuildSqlTuple tuple, Set<String> useDimCodeSet) {

        //将指标中的维度补充到维度集合中
        this.addAllDim(tuple, measure);
        this.addAllDim(useDimCodeSet, measure);

        Set<Dimension> dimensionSet = tuple.getDimensionSet();
        Set<String> displayedDimCodeSet = tuple.getDisplayDimensionCodeSet();

        List<Table> tableList = measure.getFactTable();
        for (Table table : tableList) {
            //指标表达式
            measure.setExpression(table.getExpression());
            MeasureType applyType = table.getApplyType();

            if (MeasureType.EXTENDED.equals(applyType) || MeasureType.DERIVED.equals(applyType)) {
                //衍生\派生指标
                measure.setMeasType(applyType);
                Set<String> sonDimCodeSet = new HashSet(useDimCodeSet);
                this.indJsonToExp(measure, sourceTableMap, tuple, sonDimCodeSet);
                tuple.getIndDerMeasureSet().add(measure);

            } else {
                /**
                 * 原生指标
                 * 判断table是否为可应用的事实表。
                 */
                if (this.hasAllDim(table, dimensionSet, measure, useDimCodeSet, tableList)) {
                    tuple.getUseTableSet().add(table.getTableName());
                    measure.setMeasType(MeasureType.ORIGIN);
                    this.getRootTable(table, sourceTableMap, tuple);
                    break;
                }
            }

        }

    }

    @Override
    public Map<String, List<SingleFactTableSqlAgg>> getBySourceTable(BuildSqlTuple tuple) {

        tuple.initIdx();

        Set<Dimension> dimensionSet = tuple.getDimensionSet();
        Set<Measure> measureSet = tuple.getMeasureSet();

        //此处将所有派生指标中所含有的衍生维度替换成基本维度
        this.pretreatment(measureSet);

        Set<String> displayedDimCodeSet = tuple.getDisplayDimensionCodeSet();

        Map<String, List<SingleFactTableSqlAgg>> sourceTableMap = new LinkedHashMap<>();

        //构建rootTable 既 SingleFactTableSqlAgg
        for (Measure measure : measureSet) {
            Set<String> useDimCodeSet = new HashSet(tuple.getDisplayDimensionCodeSet());
            this.buildRootTable(measure, sourceTableMap, tuple, useDimCodeSet);
        }

        /**
         *   二级维度处理，流程上必须先处理join的维度，后处理标准维度。
         * 原因是当一个数据源所包含的某一个具体维度时，此维度同时拥有独立维表、三方杂项维时，
         * 需要优先以杂项维方式进行join处理，才能保证最大的维度集合。
         */
        for (Dimension dimension : dimensionSet) {
            buildJoin(sourceTableMap, dimension, tuple);
            buildStand(sourceTableMap, dimension, tuple);
        }

        //基础维度的处理，并且保证所有已知的事实表
//        for (Dimension dimension : dimensionSet) {
//            buildStand(sourceTableMap, dimension, tuple);
//        }

//        Set<String> displayMeasureCodeSet = tuple.getDisplayMeasureCodeSet();
        //原生、衍生、派生指标
//        for (Measure measure : tuple.getChoiceMeasureSet()) {
        Set<String> useMeasureCodeSet = tuple.getUseAllMeasCode();
        for (Measure measure : tuple.getMeasureSet()) {
            //派生指标所有的where条件集
            List<Table> exTableList = new LinkedList<>();
            StringBuffer exCodeBuilder = new StringBuffer();
            String measCode = measure.getCode();

//            if (displayMeasureCodeSet.contains(measCode)) {
            if (useMeasureCodeSet.contains(measCode)) {
                Set<String> useDimCodeSet = new HashSet(tuple.getDisplayDimensionCodeSet());
                this.buildMeasure(measure, exCodeBuilder, exTableList, sourceTableMap, tuple, useDimCodeSet, new LinkedList(), true);
            }

        }

        this.getDimTableInfo(tuple);

        this.testFilter(tuple);

        return sourceTableMap;

    }

    private void testFilter(BuildSqlTuple tuple) {

        List<Filter> filterList = tuple.getQueryParam().getFilterList();

        if (!CollectionUtils.isEmpty(filterList)) {
            for (Filter filter : filterList) {

                if (!ChartQueryServiceImpl.isMeasure(filter)) {
                    boolean use = filter.isUse();
                    if (!use) {
                        throw new RuntimeException("筛选项数据并未使用，请确认数据正确性：" + filter.getCode());
                    }
                }

            }
        }

    }

    /**
     *
     * @param measure
     * @param useDimCode
     * @return
     */
    private void visitMeasUseDimCode(Measure measure, Set<String> useDimCodeSet) {

        Set<Dimension> hasAllDimensionSet = measure.getHasAllDimensionSet();
        for (Dimension dim : hasAllDimensionSet) {
            useDimCodeSet.add(dim.getCode());
        }

        Set<Measure> hasAllMeasureSet = measure.getHasAllMeasureSet();
        for (Measure sonMeas : hasAllMeasureSet) {
            this.visitMeasUseDimCode(sonMeas, useDimCodeSet);
        }

    }

    private void buildMeasure(Measure measure, StringBuffer exCodeBuilder, List<Table> exTableList, Map<String, List<SingleFactTableSqlAgg>> sourceTableMap, BuildSqlTuple tuple, Set<String> useDimCodeSet, List sonExTableList, boolean isRoot) {

        Set<Dimension> dimensionSet = tuple.getDimensionSet();
        Set<String> displayedDimCodeSet = tuple.getDisplayDimensionCodeSet();
        Set<String> currentDimCodeSet = new HashSet(displayedDimCodeSet);
        currentDimCodeSet.addAll(useDimCodeSet);

        this.visitMeasUseDimCode(measure, currentDimCodeSet);

        List<Table> tableList = measure.getFactTable();
        for (Table table : tableList) {
            boolean hasAllDimResult = this.hasAllDim(table, dimensionSet, measure, currentDimCodeSet, tableList);
            //判断table是否为可应用的事实表。
            if (MeasureType.EXTENDED.equals(measure.getMeasType()) || MeasureType.DERIVED.equals(measure.getMeasType()) || hasAllDimResult) {
                this.buildMeasure(measure, table, exCodeBuilder, exTableList, sourceTableMap, tuple, currentDimCodeSet, sonExTableList, isRoot);
                break;
            }
        }

    }

    private void indJsonToExp(Measure measure, Map<String, List<SingleFactTableSqlAgg>> sourceTableMap, BuildSqlTuple tuple, Set<String> useDimCodeSet) {

        String json = measure.getExpression();
        List<OperationItem> expressionList = measure.getExpList();
        if (org.springframework.util.CollectionUtils.isEmpty(expressionList)) {
            expressionList = JSON.parseObject(json, new TypeReference<ArrayList<OperationItem>>() {});
        }
        String division = "/";
        String add = "+";
        String sub = "-";

        //fixme 先处理没有括号的情况，有括号会出错。
        boolean nextDivision = false;
        List<String> expList = new LinkedList<>();

        StringBuilder builder = new StringBuilder();
        for (OperationItem expression : expressionList) {
            if (OperationItem.OPERAND.equalsIgnoreCase(expression.getOperatingType())) {
                String value = "{" + expression.getOperand().getMeasCode() + "}";
                builder.append(value);
                if (nextDivision) {
                    expList.add(value);
                }

            } else if (OperationItem.OPERATOR.equalsIgnoreCase(expression.getOperatingType())) {

                builder.append(expression.getOperator());

                String operator = expression.getOperator();
                if (division.equals(operator)) {
                    nextDivision = true;
                } else if (add.equals(operator)) {
                    nextDivision = false;
                } else if (sub.equals(operator)) {
                    nextDivision = false;
                }

            } else if (OperationItem.CONSTANT.equalsIgnoreCase(expression.getOperatingType())) {
                builder.append(expression.getConstant());
            }
        }

        if (expList.size() > 0 && false) {
            StringBuilder caseWhenBuilder = new StringBuilder();
            caseWhenBuilder.append("case when ");
            for (int i = 0; i < expList.size(); i++) {
                String value = expList.get(i);
                if (i > 0) {
                    caseWhenBuilder.append(" or ");
                }
                caseWhenBuilder.append(value).append("=").append(0);
            }
            String defValue = "null";
            if (tuple.isAggSql()) {
                defValue = "0";
            }
            caseWhenBuilder.append(" then ").append(defValue).append(" else ").append(builder).append(" end ");
            measure.setExpression(caseWhenBuilder.toString());
        } else {
            measure.setExpression(builder.toString());
        }

        Set<Measure> hasAllMeasureSet = measure.getHasAllMeasureSet();

        for (Measure hasMeasure : hasAllMeasureSet) {
            this.buildRootTable(hasMeasure, sourceTableMap, tuple, useDimCodeSet);
        }

    }

    private void buildBaseMeasure(Measure measure, Table factTable, Measure hasMeasure, StringBuffer exCodeBuilder, List<Table> hasFactTableList, Map<String, List<SingleFactTableSqlAgg>> rootMap, BuildSqlTuple tuple, List orgExTableList) {

        if (CollectionUtils.isEmpty(hasFactTableList)) {
            //基础base
            this.visitBaseMeasure(measure, factTable, rootMap, tuple);
            this.visitFillNullMeasure(measure, factTable, rootMap);
        } else {
            this.visitExBaseMeasure(measure, factTable, hasMeasure, exCodeBuilder, hasFactTableList, rootMap, tuple, orgExTableList);
            this.visitExFillNullMeasure(measure, hasMeasure, factTable, rootMap, exCodeBuilder);
        }

    }

    private void buildBaseMeasure(Measure measure, Table factTable, Map<String, List<SingleFactTableSqlAgg>> rootMap, BuildSqlTuple tuple, List orgExTableList) {
        this.buildBaseMeasure(measure, factTable, null, null,null, rootMap, tuple, orgExTableList);
    }

    /**
     * 判断code是否是当前指标的子维度
     * @param measure
     * @return
     */
    private boolean isSonMeasure(Measure measure, String code) {

        boolean isSon = false;

        List<OperationItem> expList = measure.getExpList();
        for (OperationItem operaItem : expList) {

            OperationItem.MeasureBasicInfo measBasicInfo = operaItem.getOperand();
            if (null != measBasicInfo) {
                String measCode = measBasicInfo.getMeasCode();
                if (StringUtil.isNotEmpty(measCode) && measCode.equalsIgnoreCase(code)) {
                    isSon = true;
                    break;
                }
            }

        }

        return isSon;

    }

    private void buildMeasure(Measure measure, Table factTable, StringBuffer exCodeBuilder, List<Table> exTableList, Map<String, List<SingleFactTableSqlAgg>> rootMap, BuildSqlTuple tuple, Set<String> useDimCodeSet, List orgExTableList, boolean isRoot) {
        //指标类型 原生、派生、衍生.此处只处理源生、派生。
        MeasureType type = factTable.getApplyType();
        String table = factTable.getTableName();
        Set<String> displayDimensionCodeSet = tuple.getDisplayDimensionCodeSet();
        Set<String> allDimCodeSet = tuple.getAllDimCode();
        Set<Dimension> dimensionSet = tuple.getDimensionSet();

        Set<String> sonDimCodeSet = new HashSet(useDimCodeSet);
        sonDimCodeSet.addAll(displayDimensionCodeSet);
        this.visitMeasUseDimCode(measure, sonDimCodeSet);

        if (MeasureType.ORIGIN.equals(type)) {
            //基础base
            this.buildBaseMeasure(measure, factTable, null, null, exTableList, rootMap, tuple, orgExTableList);

        } else if (MeasureType.EXTENDED.equals(type) || MeasureType.DERIVED.equals(type)) {
//            //获取派生下面的所有指标；

            exTableList.add(factTable);
            exCodeBuilder.append("_");
            exCodeBuilder.append(measure.getCode());

            Set<Dimension> hasAllDimensionSet = measure.getHasAllDimensionSet();
            Set<Measure> hasAllMeasureSet = measure.getHasAllMeasureSet();

            for (Measure hasMeasure : hasAllMeasureSet) {

                if (isRoot) {
                    orgExTableList = new LinkedList();
                }

                orgExTableList.add(factTable);

                if (!this.isSonMeasure(measure, hasMeasure.getCode())) {
                    continue;
                }

                //涵盖的指标要继承父指标的维度
                hasMeasure.getHasAllDimensionSet().addAll(hasAllDimensionSet);

                MeasureType hasMeasType = hasMeasure.getMeasType();
                if (MeasureType.ORIGIN.equals(hasMeasType)) {
                    List<Table> hasFactTableList = hasMeasure.getFactTable();
                    for (Table hasFactTable : hasFactTableList) {

                        /**
                         * 此处维度范围应该取当前被选择的维度以及派生指标父所需要的维度
                         */
                        Set<Dimension> scopeAllDimSet = measure.getHasAllDimensionSet();
                        scopeAllDimSet.addAll(tuple.getChoiceDimensionSet());

                        if (this.hasAllDim(hasFactTable, scopeAllDimSet, measure, sonDimCodeSet, hasFactTableList)) {
                            hasMeasure.setMeasType(hasMeasType);
                            StringBuffer sonExCodeBuilder = new StringBuffer();
                            sonExCodeBuilder.append(exCodeBuilder);
                            this.buildBaseMeasure(hasMeasure, hasFactTable, measure, sonExCodeBuilder, exTableList, rootMap, tuple, orgExTableList);
                            break;
                        } else {
                            System.out.println("EEEEE");
//                            this.hasAllDim(hasFactTable, scopeAllDimSet, measure, sonDimCodeSet, hasFactTableList);
                        }

                    }
                } else if (MeasureType.EXTENDED.equals(hasMeasType) || MeasureType.DERIVED.equals(hasMeasType)) {
                    StringBuffer sonExCodeBuilder = new StringBuffer();
                    sonExCodeBuilder.append(exCodeBuilder);
                    this.buildMeasure(hasMeasure, sonExCodeBuilder, exTableList, rootMap, tuple, useDimCodeSet, orgExTableList, false);
                }
            }
        }

    }

    public void visitExFillNullMeasure(Measure measure, Measure exMeasure, Table factTable, Map<String, List<SingleFactTableSqlAgg>> rootMap, StringBuffer exCodeBuilder) {

        String source = String.valueOf(factTable.getSourceType());
        String table = factTable.getTableName();

        Set<String> sourceSet = rootMap.keySet();
        for (String sor : sourceSet) {

            List<SingleFactTableSqlAgg> singleFactTableSqlAggs = rootMap.get(sor);
            for (SingleFactTableSqlAgg singleFactTableSqlAgg : singleFactTableSqlAggs) {
                if (!table.equalsIgnoreCase(singleFactTableSqlAgg.getName())) {

                    String asName = "#q#ex" + getColumnAlias(measure) + "" + exCodeBuilder + "#q#";
                    String column = "null as #q#ex" + getColumnAlias(measure) + "" + exCodeBuilder + "#q#";

//                    singleFactTableSqlAgg.getColumnList().add(column);
                    this.addOnlyValue(singleFactTableSqlAgg.getColumnList(), column);

                    MeasureSonSelectTempTable measureSonSelectTempTable = new MeasureSonSelectTempTable();
                    measureSonSelectTempTable.setFillNull(true);
                    measureSonSelectTempTable.setMeasure(measure);
                    measureSonSelectTempTable.setColumn("0");
                    measureSonSelectTempTable.setAsName(asName);
                    measureSonSelectTempTable.setId(UUID.randomUUID().toString());

                    singleFactTableSqlAgg.getMeasureSonSelectTempTableSet().add(measureSonSelectTempTable);

                }
            }

        }

    }

    private List<Filter> getFilterList(List<Table> exTableList) {

        List<Filter> filterList = new LinkedList<>();
        if (!CollectionUtils.isEmpty(exTableList)) {
            for (Table table : exTableList) {
                if (!CollectionUtils.isEmpty(table.getFilterList())) {
                    filterList.addAll(table.getFilterList());
                }
            }
        }

        return filterList;

    }

    /**
     * 判断指标是否存在于维度指标过滤权限中
     * @param measCode
     * @param filter
     * @return
     */
    private boolean isExist(String measCode, Filter filter, String exMeasCode) {

        String orgCode = exMeasCode.replaceFirst("_", "");

        boolean isExist = false;
        for (AuthElementMeasure authEleMeas : filter.getAuthElementMeasureSet()) {
            if (measCode.equalsIgnoreCase(authEleMeas.getMeasCode()) || orgCode.indexOf(authEleMeas.getMeasCode()) == 0) {
                isExist = true;
                break;
            }
        }
        return isExist;
    }

    /**
     * 构建派生基础BaseMeasure.
     */
    public void visitExBaseMeasure(Measure measure, Table factTable, Measure exMeasure, StringBuffer exCodeBuilder, List<Table> exTableList, Map<String, List<SingleFactTableSqlAgg>> rootMap, BuildSqlTuple tuple, List orgExTableList) {

        SingleFactTableSqlAgg rootTable = this.getRootTable(factTable, rootMap, tuple);
        String alias = rootTable.getAlias();

        String exp = this.getExpStr(factTable);
        String expColumn = factTable.getFactColumn();
        String whereCondition = setAlias(factTable.getWhereCondition(), alias);

        Set<MeasureSonSelectTempTable> measureSonSelectTempTableSet = rootTable.getMeasureSonSelectTempTableSet();
        MeasureSonSelectTempTable measureSonSelectTempTable = new MeasureSonSelectTempTable();
        measureSonSelectTempTable.setWhereCondition(whereCondition);
        measureSonSelectTempTable.setMeasure(measure);
        measureSonSelectTempTableSet.add(measureSonSelectTempTable);

        //此处应该是多个派生指标的whereList
        //所有维度集合
        Set<Dimension> allDimSet = new LinkedHashSet<>();
        allDimSet.addAll(tuple.getDimensionSet());
        allDimSet.addAll(measure.getHasAllDimensionSet());

        List<Filter> allFilterList = tuple.getQueryParam().getFilterList();
        List<Filter> measFilterList = new ArrayList<>();
        for (Filter filter : allFilterList) {

            if (this.isExist(measure.getCode(), filter, exCodeBuilder.toString())) {
                measFilterList.add(filter);
            }
        }

        List<Filter> tempFilterList = new ArrayList<>();
        tempFilterList.addAll(measFilterList);

        List<Filter> dimFilterList = this.getFilterList(exTableList);
//        tempFilterList.addAll(dimFilterList);

        List<Filter> dimOrgFilterList = this.getFilterList(orgExTableList);
//        System.out.println(dimOrgFilterList);
        tempFilterList.addAll(dimOrgFilterList);

        for (Filter filter : tempFilterList) {

            String dimCode = filter.getCode();
            Dimension dim = this.findDimension(dimCode, allDimSet);

            List<Table> dimFactTableList = dim.getFactTableList();
            Table dimFactTable = this.findTable(factTable.getTableName(), dimFactTableList);

            if (null != dimFactTable) {

                //默认取事实表
                String dimAlias = alias;

                filter.setDimAlias(dimAlias);
                filter.setAlias(dimAlias);

                String dimColumn = dimFactTable.getFactColumn();
                String dimColumnName = dimFactTable.getDimColumn();
                List<Table> dimTableList = dim.getDimTableList();
                for (Table dimTable : dimTableList) {
                    List<LeftJoinDimTable> leftJoinList = rootTable.getLeftJoinedDimTableList();
                    for (LeftJoinDimTable leftJoin : leftJoinList) {
                        if (leftJoin.getTableName().equals(dimTable.getTableName())
                                && this.isHierarchy(dim, leftJoin.getDim())) {
                            //如果存在则取维度表
                            if (!leftJoin.isSelfGroupId()) {
                                dimAlias = leftJoin.getAlias();
                            }
                            dimColumn = dimTable.getDimPrimaryKey();
                            dimColumnName = dimTable.getDimColumn();

                            filter.setDimAlias(dimAlias);
                            filter.setAlias(dimAlias);
                            filter.setColumn(dimColumn);
                            filter.setDimColumnName(dimColumnName);

                            break;

                        }
                    }
                }

                filter.setColumn(dimColumn);
                Boolean isMaster = tuple.isMasterDimInFactTable(factTable.getTableName(), factTable.getFactColumn(), dim.getCode());
                
                if (DimType.DEGENERATE_DIM.equals(dim.getDimType()) || DimType.STD_WITHOUT_TABLE.equals(dim.getDimType()) || isMaster) {
                    //退化维可能无维表，此处跟主维度一样默认给事实表别名
                    filter.setDimAlias(alias);
                    filter.setAlias(alias);
                    filter.setColumnId(dimFactTable.getFactColumn());
                    filter.setColumn(dimFactTable.getFactColumn());
                }

                if (CollectionUtils.isEmpty(filter.getAuthElementMeasureSet())) {
                    measureSonSelectTempTable.getExFilterList().add(filter);
                } else {
                    measureSonSelectTempTable.getMeasFilterList().add(filter);
                }

            } else {
                String errInfo = "measure code:" + measure.getCode() + " name:" + measure.getName() + " in factTable : " + factTable.getTableName() + "  it's not found in this table.    dimCode:" + dim.getCode() + "  dimName:" + ((Dimension) dim).getName();
                throw new RuntimeException(errInfo);
            }

        }

        String measColumn = alias + "." + expColumn;
        String rootExCode = exCodeBuilder.toString();
        //派生指标code
        String asName = "#q#ex" + getColumnAlias(measure) + rootExCode + "#q#";
        String columnDoris = this.getExp(exp, measColumn, factTable.getFactColumnType()) +  " as " + asName;
        //where处理，生成对应的case when.
        measureSonSelectTempTable.setColumn(columnDoris);
        measureSonSelectTempTable.setAsName(asName);
        measureSonSelectTempTable.setExMeasCode(rootExCode);

        rootTable.getMeasureSet().add(measure);

    }

    /**
     * 如果未设置level此方法返回true。
     * @param dim
     * @param joinDim
     * @return
     */
    private boolean isHierarchy(Dimension dim, Dimension joinDim) {

        Level level = dim.getLevel();
        Level joinLevel = joinDim.getLevel();
        boolean isHierarchy = false;
        if (null != level && null != joinLevel) {
            String hierarchyCode = level.getHierarchyCode();
            String joinHierarchyCode = joinLevel.getHierarchyCode();
            isHierarchy = hierarchyCode.equalsIgnoreCase(joinHierarchyCode);
        } else if (null == level && null == joinLevel) {
            isHierarchy = true;
        }

        return isHierarchy;

    }

    /**
     * 衍生指标
     * @param exp
     * @param column
     * @return
     */
    private String getExp(String exp, String column, String type) {
        String expStr = null;
        if (SqlAggFunType.COUNT.getDesc().equalsIgnoreCase(exp)) {
            expStr = "count(" + column + ")";
            expStr = "case when " + expStr + " = 0 then null else " + expStr + " end";
        } else if (SqlAggFunType.DISTINCTCOUNT.getDesc().equalsIgnoreCase(exp)) {
            expStr = "count(distinct " + column + ")";
            expStr = "case when " + expStr + " = 0 then null else " + expStr + " end";
        } else if (SqlAggFunType.MAX.getDesc().equalsIgnoreCase(exp)) {
            expStr = "max(" + column + ")";
        } else if (SqlAggFunType.MIN.getDesc().equalsIgnoreCase(exp)) {
            expStr = "min(" + column + ")";
        } else if (SqlAggFunType.AVG.getDesc().equalsIgnoreCase(exp)) {
            expStr = "avg(" + column + ")";
        } else if (SqlAggFunType.PERCENTILE_APPROX50.getDesc().equalsIgnoreCase(exp)) {
            // MySQL 不支持 percentile_approx，用 GROUP_CONCAT+SUBSTRING_INDEX 模拟（受 group_concat_max_len 限制）
            expStr = "CAST(SUBSTRING_INDEX(SUBSTRING_INDEX(GROUP_CONCAT(" + column + " ORDER BY " + column + " SEPARATOR '|'), '|', CEILING(0.51 * COUNT(" + column + "))), '|', -1) AS DECIMAL(20,6))";
        } else if (SqlAggFunType.PERCENTILE_APPROX90.getDesc().equalsIgnoreCase(exp)) {
            expStr = "CAST(SUBSTRING_INDEX(SUBSTRING_INDEX(GROUP_CONCAT(" + column + " ORDER BY " + column + " SEPARATOR '|'), '|', CEILING(0.90 * COUNT(" + column + "))), '|', -1) AS DECIMAL(20,6))";
        } else if (SqlAggFunType.PERCENTILE_APPROX95.getDesc().equalsIgnoreCase(exp)) {
            expStr = "CAST(SUBSTRING_INDEX(SUBSTRING_INDEX(GROUP_CONCAT(" + column + " ORDER BY " + column + " SEPARATOR '|'), '|', CEILING(0.95 * COUNT(" + column + "))), '|', -1) AS DECIMAL(20,6))";
        } else if (SqlAggFunType.PERCENTILE_APPROX99.getDesc().equalsIgnoreCase(exp)) {
            expStr = "CAST(SUBSTRING_INDEX(SUBSTRING_INDEX(GROUP_CONCAT(" + column + " ORDER BY " + column + " SEPARATOR '|'), '|', CEILING(0.99 * COUNT(" + column + "))), '|', -1) AS DECIMAL(20,6))";
        } else if (SqlAggFunType.STDDEV.getDesc().equalsIgnoreCase(exp)) {
            expStr = "stddev(" + column + ")";
        } else {
            expStr = "sum(" + column + ")";
        }

        return expStr;
    }

    private String getCaseWhenValue(String exp, String measColumn) {

        String result = null;
        if (SqlAggFunType.COUNT.getDesc().equalsIgnoreCase(exp) || SqlAggFunType.DISTINCTCOUNT.getDesc().equalsIgnoreCase(exp)) {
            result = "null";
        } else {
            result = "0";
        }

        return result;

    }

    private Table findTable(String tableName, List<Table> allTableList) {

        Table table = null;
        for (Table factTable : allTableList) {
            if (tableName.equals(factTable.getTableName())) {
                table = factTable;
                break;
            }
        }

        return table;

    }

    private Dimension findDimension(String dimCode, Set<Dimension> allDimensionSet) {

        Dimension dim = null;

        for (Dimension dimension : allDimensionSet) {
            if (dimension.getCode().equals(dimCode)) {
                dim = dimension;
                break;
            }
        }

        if (null == dim) {
            //fix bug
            dim = MemCacheUtils.getDimensionTableInfo(this.indicatorService, dimCode);
        }

        return dim;

    }

    public void visitFillNullMeasure(Measure measure, Table factTable, Map<String, List<SingleFactTableSqlAgg>> rootMap) {

        String source = String.valueOf(factTable.getSourceType());
        String table = factTable.getTableName();

        Set<String> sourceSet = rootMap.keySet();
        for (String sor : sourceSet) {

            List<SingleFactTableSqlAgg> singleFactTableSqlAggs = rootMap.get(sor);
            for (SingleFactTableSqlAgg singleFactTableSqlAgg : singleFactTableSqlAggs) {
                if (!table.equalsIgnoreCase(singleFactTableSqlAgg.getName())) {
                    String asName = "#q#" + getColumnAlias(measure) + "#q#";
                    String column = "null as " + asName;

//                    singleFactTableSqlAgg.getColumnList().add(column);
                    this.addOnlyValue(singleFactTableSqlAgg.getColumnList(), column);

                    MeasureSonSelectTempTable measureSonSelectTempTable = new MeasureSonSelectTempTable();
                    measureSonSelectTempTable.setFillNull(true);
                    measureSonSelectTempTable.setMeasure(measure);
                    measureSonSelectTempTable.setColumn("0");
                    measureSonSelectTempTable.setAsName(asName);

                    singleFactTableSqlAgg.getMeasureSonSelectTempTableSet().add(measureSonSelectTempTable);

                }
            }

        }

    }

    /**
     * 构建基础BaseMeasure.
     */
    public void visitBaseMeasure(Measure measure, Table factTable, Map<String, List<SingleFactTableSqlAgg>> rootMap, BuildSqlTuple tuple) {

        SingleFactTableSqlAgg rootTable = this.getRootTable(factTable, rootMap, tuple);

        String alias = rootTable.getAlias();

        String exp = this.getExpStr(factTable);
        String expStr = this.getExp(exp, alias, factTable);

        String asName = "#q#" + getColumnAlias(measure) + "#q#";
//        String column = expStr + " as #q#" + getColumnAlias(measure) + "#q#";
        String column = expStr + " as " + asName;
        String whereCondition = setAlias(factTable.getWhereCondition(), alias);

        //where处理，生成对应的case when.
        MeasureSonSelectTempTable measureSonSelectTempTable = new MeasureSonSelectTempTable();
        measureSonSelectTempTable.setWhereCondition(whereCondition);
        measureSonSelectTempTable.setMeasure(measure);
        measureSonSelectTempTable.setColumn(column);
        measureSonSelectTempTable.setAsName(asName);

        rootTable.getMeasureSonSelectTempTableSet().add(measureSonSelectTempTable);


//        rootTable.getColumnList().add(column);
        this.addOnlyValue(rootTable.getColumnList(), column);
        rootTable.getMeasureSet().add(measure);

        //基础指标的权限筛选过滤项
        List<Filter> allFilterList = tuple.getQueryParam().getFilterList();
        List<Filter> measFilterList = new ArrayList<>();
        for (Filter filter : allFilterList) {

            if (this.isExist(measure.getCode(), filter, "nilExCode")) {
                measFilterList.add(filter);
            }
        }

        Set<Dimension> allDimSet = new LinkedHashSet<>();
        allDimSet.addAll(tuple.getDimensionSet());
        allDimSet.addAll(measure.getHasAllDimensionSet());

        for (Filter filter : measFilterList) {

            String dimCode = filter.getCode();
            Dimension dim = this.findDimension(dimCode, allDimSet);

            List<Table> dimFactTableList = dim.getFactTableList();
            Table dimFactTable = this.findTable(factTable.getTableName(), dimFactTableList);

            if (null != dimFactTable) {

                //默认取事实表
                String dimAlias = alias;

                //给默认值取事实表别名
                filter.setDimAlias(dimAlias);
                filter.setAlias(dimAlias);

                String dimColumn = dimFactTable.getFactColumn();
                String dimColumnName = dimFactTable.getDimColumn();
                List<Table> dimTableList = dim.getDimTableList();
                for (Table dimTable : dimTableList) {
                    List<LeftJoinDimTable> leftJoinList = rootTable.getLeftJoinedDimTableList();
                    for (LeftJoinDimTable leftJoin : leftJoinList) {
                        if (leftJoin.getTableName().equals(dimTable.getTableName())
                                && this.isHierarchy(dim, leftJoin.getDim())) {
                            //如果存在则取维度表
                            if (!leftJoin.isSelfGroupId()) {
                                dimAlias = leftJoin.getAlias();
                            }
                            dimColumn = dimTable.getDimPrimaryKey();
                            dimColumnName = dimTable.getDimColumn();

                            filter.setDimAlias(dimAlias);
                            filter.setAlias(dimAlias);
                            filter.setColumn(dimColumn);
                            filter.setDimColumnName(dimColumnName);

                            break;

                        }
                    }
                }

                filter.setColumn(dimColumn);
                Boolean isMaster = tuple.isMasterDimInFactTable(factTable.getTableName(), factTable.getFactColumn(), dim.getCode());
                boolean isStdWithoutTable = DimType.STD_WITHOUT_TABLE.equals(dim.getDimType()) && null == filter.getAlias();

                if (DimType.DEGENERATE_DIM.equals(dim.getDimType()) || isMaster || isStdWithoutTable) {
                    //退化维可能无维表，此处跟主维度一样默认给事实表别名
                    filter.setDimAlias(alias);
                    filter.setAlias(alias);
                    filter.setColumnId(dimFactTable.getFactColumn());
                    filter.setColumn(dimFactTable.getFactColumn());
                }

                measureSonSelectTempTable.getMeasFilterList().add(filter);

            } else {
                String errInfo = "measure code:" + measure.getCode() + " name:" + measure.getName() + " in factTable : " + factTable.getTableName() + "  it's not found in this table.    dimCode:" + dim.getCode() + "  dimName:" + ((Dimension) dim).getName();
                throw new RuntimeException(errInfo);
            }

        }

    }

    private String getExpStr(Table factTable) {
        String expStr = factTable.getExpression();
        String exp = null;
        List<OperationItem> expressionList = JSON.parseObject(expStr, new TypeReference<ArrayList<OperationItem>>() {});
        for (OperationItem expression : expressionList) {
            if (OperationItem.OPERATOR.equalsIgnoreCase(expression.getOperatingType())) {
                exp = expression.getOperator();
            }
        }
        return exp;
    }

    /**
     * 基础指标
     * @param exp
     * @param alias
     * @param factTable
     * @return
     */
    public static String getExp(String exp, String alias, Table factTable) {

        String column = factTable.getFactColumn();
        String type = factTable.getFactColumnType();
        SourceType source = factTable.getSourceType();

        String expStr = null;
        if (SqlAggFunType.COUNT.getDesc().equalsIgnoreCase(exp)) {
            expStr = "count(" + alias + "." + column + ")";
            expStr = "case when " + expStr + " = 0 then null else " + expStr + " end";
        } else if (SqlAggFunType.DISTINCTCOUNT.getDesc().equalsIgnoreCase(exp)) {
            expStr = "count(distinct " + alias + "." + column + ")";
            expStr = "case when " + expStr + " = 0 then null else " + expStr + " end";
        } else if (SqlAggFunType.MAX.getDesc().equalsIgnoreCase(exp)) {
            expStr = "max(" + alias + "." + column + ")";
        } else if (SqlAggFunType.MIN.getDesc().equalsIgnoreCase(exp)) {
            expStr = "min(" + alias + "." +  column + ")";
        } else if (SqlAggFunType.AVG.getDesc().equalsIgnoreCase(exp)) {
            expStr = "avg(" + alias + "." +  column + ")";
        } else if (SqlAggFunType.PERCENTILE_APPROX50.getDesc().equalsIgnoreCase(exp)) {
            expStr = "CAST(SUBSTRING_INDEX(SUBSTRING_INDEX(GROUP_CONCAT(" + alias + "." + column + " ORDER BY " + alias + "." + column + " SEPARATOR '|'), '|', CEILING(0.50 * COUNT(" + alias + "." + column + "))), '|', -1) AS DECIMAL(20,6))";
        } else if (SqlAggFunType.PERCENTILE_APPROX90.getDesc().equalsIgnoreCase(exp)) {
            expStr = "CAST(SUBSTRING_INDEX(SUBSTRING_INDEX(GROUP_CONCAT(" + alias + "." + column + " ORDER BY " + alias + "." + column + " SEPARATOR '|'), '|', CEILING(0.90 * COUNT(" + alias + "." + column + "))), '|', -1) AS DECIMAL(20,6))";
        } else if (SqlAggFunType.PERCENTILE_APPROX95.getDesc().equalsIgnoreCase(exp)) {
            expStr = "CAST(SUBSTRING_INDEX(SUBSTRING_INDEX(GROUP_CONCAT(" + alias + "." + column + " ORDER BY " + alias + "." + column + " SEPARATOR '|'), '|', CEILING(0.95 * COUNT(" + alias + "." + column + "))), '|', -1) AS DECIMAL(20,6))";
        } else if (SqlAggFunType.PERCENTILE_APPROX99.getDesc().equalsIgnoreCase(exp)) {
            expStr = "CAST(SUBSTRING_INDEX(SUBSTRING_INDEX(GROUP_CONCAT(" + alias + "." + column + " ORDER BY " + alias + "." + column + " SEPARATOR '|'), '|', CEILING(0.99 * COUNT(" + alias + "." + column + "))), '|', -1) AS DECIMAL(20,6))";
        } else if (SqlAggFunType.STDDEV.getDesc().equalsIgnoreCase(exp)) {
            expStr = "stddev(" + alias + "." + column + ")";
        } else {
            expStr = "sum(" + alias + "." + column + ")";
        }

        return expStr;
    }

    private boolean isUseMeasure(BuildSqlTuple tuple, String code) {

        Set<String> useMeasureCodeSet = tuple.getUseAllMeasCode();
        boolean isHas = false;
        if (!CollectionUtils.isEmpty(useMeasureCodeSet)) {
            isHas = useMeasureCodeSet.contains(code);
        }

        return isHas;

    }

    private boolean isDisplayMeasure(BuildSqlTuple tuple, String code) {

        Set<String> displayedMeasureCodeSet = tuple.getDisplayMeasureCodeSet();
        boolean isHas = false;
        if (!CollectionUtils.isEmpty(displayedMeasureCodeSet)) {
            isHas = displayedMeasureCodeSet.contains(code);
        }

        return isHas;

    }

    private boolean isUseMeasure(BuildSqlTuple tuple, Measure meas, Set<Measure> measureSet) {

        String code = meas.getCode();
        String measName = meas.getName();
        MeasureType measType = meas.getMeasType();
        boolean isDisplay = this.isUseMeasure(tuple, code);

        if (isDisplay) {
            return true;
        }

        if (MeasureType.EXTENDED.equals(measType)) {
            return true;
        }

        /**
         * 在衍生指标中是否有应用
         */
        for (Measure measure : measureSet) {

            if (MeasureType.DERIVED.equals(measure.getMeasType())) {
                for (Measure hasMeas : measure.getHasAllMeasureSet()) {

                    if (hasMeas.getCode().equals(code)) {
                        return true;
                    }

                }
            }

        }

        Set<Measure> allMeasSet = new LinkedHashSet<>();
        allMeasSet.addAll(tuple.getIndDerMeasureSet());

        for (Measure measure : allMeasSet) {

            String exp = measure.getExpression();
            String expMeasName = "_" + measName + "#q#";
            if (MeasureType.DERIVED.equals(measure.getMeasType()) && !StringUtil.isEmpty(exp) && (exp.indexOf(code) > 0 || exp.indexOf(expMeasName) > 0)) {
                return true;
            }

        }

        return false;

    }

    /**
     * 派生、衍生指标下的所有指标
     *
     * @param factTable
     * @param tuple
     * @return
     */
    private void visitHasBaseMeasure(Table factTable, BuildSqlTuple tuple, Set<Measure> measureSet) {
        String exp = factTable.getExpression();
        List<OperationItem> itemBOList = JSON.parseObject(exp, new TypeReference<ArrayList<OperationItem>>() {});
        for (OperationItem itemBO : itemBOList) {
            if (OperationItem.OPERAND.equalsIgnoreCase(itemBO.getOperatingType())) {
                String measCode = itemBO.getOperand().getMeasCode();
                Measure meas = this.findMeasure(measCode, tuple);
                if (null != meas && MeasureType.ORIGIN.equals(meas.getMeasType())) {
                    measureSet.add(meas);
                } else {
                    List<Table> tableList = meas.getFactTable();
                    for (Table table : tableList) {
                        this.visitHasBaseMeasure(table, tuple, measureSet);
                    }
                }
            }
        }

    }

    private Measure findMeasure(String code, Set<Measure> measureSet) {

        Measure measure = null;

        for (Measure meas : measureSet) {
            if (meas.getCode().equals(code)) {
                measure = meas;
                break;
            }
        }

        return measure;

    }

    private Measure findMeasure(String code, BuildSqlTuple tuple) {

        Measure measure = this.findMeasure(code, tuple.getMeasureSet());
        return measure;

    }

    /**
     * 构建标准维度
     */
    private void buildStand(Map<String, List<SingleFactTableSqlAgg>> rootMap, Dimension dimension, BuildSqlTuple tuple) {

        List<Table> factTableList = dimension.getFactTableList();
        for (Table factTable : factTableList) {

            String tableName = factTable.getTableName();
            if (this.isHasTable(rootMap, tableName)) {

                //获取rootTable
                SingleFactTableSqlAgg singleFactTableSqlAgg = getRootTable(factTable, rootMap, tuple);
                boolean isLeftJoin = this.isLeftJoin(dimension, tuple);
                DimType dimType = dimension.getDimType();
                //兼容维度里面有多个相同事实表的bug
                String dimCode = singleFactTableSqlAgg.getTableUseMap().get(tableName);
                Boolean dimUsed = null != dimCode && dimCode.equalsIgnoreCase(dimension.getCode());

                if ((DimType.STD_WITH_TABLE.equals(dimType)
                        || DimType.STD_WITHOUT_TABLE.equals(dimType)) && !dimUsed) {

                    List<Table> dimTableList = dimension.getDimTableList();
                    for (Table dimTable : dimTableList) {
                        if (!isLeftJoin) {
                            //标准维度处理
                            visitStand(singleFactTableSqlAgg, factTable, dimTable, dimension, tuple);
                        }
                    }

                } else if (DimType.DEGENERATE_DIM.equals(dimType)) {
                    //退化维属性处理，事实表里直接存储文本字段
                    visitDegenerate(singleFactTableSqlAgg, factTable, dimension, tuple);
                } else if (DimType.CUSTOM.equals(dimType)) {
                    //日期粒度差值按日维度处理
                    visitDateDiff(singleFactTableSqlAgg, factTable, dimension, tuple);
                }

                singleFactTableSqlAgg.getDimensionSet().add(dimension);

            }

        }

    }

    /**
     * 日期维度日粒度差异处理
     * @param singleFactTableSqlAgg
     * @param factTable
     */
    private void visitDateDiff(SingleFactTableSqlAgg singleFactTableSqlAgg, Table factTable, Dimension dimension, BuildSqlTuple tuple) {

        String factColumn = factTable.getFactColumn();
        //datediff( T1.day_short_desc, T1.day_short_desc ) < 7
        String rootTableAlias = singleFactTableSqlAgg.getAlias();

        final String addWhere = " != 1949101";
//        String column = rootTableAlias + "." + factColumn;
        String column = setAlias(factColumn, rootTableAlias, addWhere);
        column = "cast((" + column.replaceFirst(addWhere, "") + ") as STRING)";

        factColumn = column;

        String columnName = "#q#" + getColumnAlias(dimension) + "#q#";
        String dimId = column + " as #q#" + getColumnAlias(dimension) + "#q#";

        this.addColumnGroupBy(singleFactTableSqlAgg, tuple, dimId, column, columnName, dimension);
        tuple.getDimCodeColumnMap().put(dimension.getCode(), column);
        this.visitWhere(singleFactTableSqlAgg, dimension, tuple, rootTableAlias, factColumn, factColumn);

    }

    /**
     * 退化维处理
     *
     * @param singleFactTableSqlAgg
     * @param factTable
     */
    private void visitDegenerate(SingleFactTableSqlAgg singleFactTableSqlAgg, Table factTable, Dimension dimension, BuildSqlTuple tuple) {

        String factColumn = factTable.getFactColumn();
        String rootTableAlias = singleFactTableSqlAgg.getAlias();
        String rawColumn = rootTableAlias + "." + factColumn;
        // Apply date_format for date-type dimensions
        String column = applyDateFormat(rawColumn, dimension.getViewType());
        String columnName = "#q#" + getColumnAlias(dimension) + "#q#";
        String dimId = column + " as #q#" + getColumnAlias(dimension) + "#q#";

        this.addColumnGroupBy(singleFactTableSqlAgg, tuple, dimId, column, columnName, dimension);
        tuple.getDimCodeColumnMap().put(dimension.getCode(), rawColumn);
        this.visitWhere(singleFactTableSqlAgg, dimension, tuple, rootTableAlias, factColumn, factColumn);

    }

    private String applyDateFormat(String column, ViewType viewType) {
        if (viewType == null) return column;
        switch (viewType) {
            case YEAR:   return "date_format(" + column + ", '%Y')";
            case MONTH:  return "date_format(" + column + ", '%Y-%m')";
            case WEEK:   return "date_format(" + column + ", '%Y%u')";
            case SEASON: return "CONCAT(YEAR(" + column + "), 'Q', QUARTER(" + column + "))";
            case DAY:    return "date_format(" + column + ", '%Y-%m-%d')";
            case HOUR:   return "date_format(" + column + ", '%Y-%m-%d %H')";
            default:     return column;
        }
    }

    /**
     * 标准维处理
     *
     * @param singleFactTableSqlAgg
     * @param factTable
     */
    private void visitStand(SingleFactTableSqlAgg singleFactTableSqlAgg, Table factTable, Table dimTable, Dimension dimension, BuildSqlTuple tuple) {
        String factColumn = factTable.getFactColumn();
        String rootTableAlias = singleFactTableSqlAgg.getAlias();

        if (dimension.isRootJoin()) {

            String dimTableAlias = null;
            List<LeftJoinDimTable> leftJoinedDimTableList = singleFactTableSqlAgg.getLeftJoinedDimTableList();
            for (LeftJoinDimTable leftJoinedDimTable : leftJoinedDimTableList) {
                Dimension leftDim = leftJoinedDimTable.getDim();
                boolean eqDim = leftDim.getCode().equalsIgnoreCase(dimension.getCode());
                if (dimTable.getTableName().equalsIgnoreCase(leftJoinedDimTable.getTableName()) && eqDim) {
                    dimTableAlias = leftJoinedDimTable.getAlias();
                }
            }

            String column = null;
            String filterColumn = dimTable.getDimColumn();
            String dimPrimaryKey = dimTable.getDimPrimaryKey();
            if (CollectionUtils.isEmpty(leftJoinedDimTableList) || dimTableAlias == null) {

                if (dimTableAlias == null) {
                    filterColumn = factColumn;
                    dimPrimaryKey = factColumn;
                } else {
                    filterColumn = factColumn;
                    dimPrimaryKey = factColumn;
                }

                dimTableAlias = rootTableAlias;
                column = setAlias(factColumn, dimTableAlias);

            } else {
                column = setAlias(dimTable.getDimPrimaryKey(), dimTableAlias);
            }

            String columnName = "#q#" + getColumnAlias(dimension) + "ID#q#";
            String dimId = column + " as " + columnName;

            this.addColumnGroupBy(singleFactTableSqlAgg, tuple, dimId, column, columnName, dimension);
            tuple.getDimCodeColumnMap().put(dimension.getCode(), column);

            if (this.isDateViewType(dimension.getViewType())) {
                filterColumn = dimTable.getDimPrimaryKey();
            }

            this.visitWhere(singleFactTableSqlAgg, dimension, tuple, dimTableAlias, filterColumn, dimPrimaryKey);

        } else {

            String column = setAlias(factColumn, rootTableAlias);
            String columnName = "#q#" + getColumnAlias(dimension) + "ID#q#";
            String dimId = column + " as " + columnName;

            this.addColumnGroupBy(singleFactTableSqlAgg, tuple, dimId, column, columnName, dimension);
            tuple.getDimCodeColumnMap().put(dimension.getCode(), column);

            this.visitWhere(singleFactTableSqlAgg, dimension, tuple, rootTableAlias, factColumn, factColumn);

        }

    }

    public static boolean hasFactColumnByDimTable(String masterColumn, List<DwColumn> columnList) {

        for (DwColumn dwColumn : columnList) {
            if (dwColumn.getName().equalsIgnoreCase(masterColumn)) {
                return true;
            }
        }
        return false;

    }

    /**
     * 层次维度
     *   主次维度，此方法主要解决需要join查询的维度信息，如通过唯一的主维度日，进行月或年的分组查询。
     *
     * @param rootMap
     * @param dimension
     */
    private void buildJoin(Map<String, List<SingleFactTableSqlAgg>> rootMap, Dimension dimension, BuildSqlTuple tuple) {

        Set<String> existsTable = new LinkedHashSet<>();
        List<Table> factTableList = dimension.getFactTableList();
        for (Table factTable : factTableList) {

            String tableName = factTable.getTableName();

            //维度表所存在的事实表是本次查询需要的事实表，并且维度类型是标准维（有维表、无维表两种情况）。
            if (this.isHasTable(rootMap, tableName)
                    && (DimType.STD_WITHOUT_TABLE.equals(dimension.getDimType())
                    || DimType.STD_WITH_TABLE.equals(dimension.getDimType()))) {

                String masterPrimaryKey = factTable.getMasterPrimaryKey();

                List<Table> dimTableList = dimension.getDimTableList();
                for (Table dimTable : dimTableList) {

                    List<DwColumn> dwColumnList = new ArrayList<DwColumn>();
                    dwColumnList.addAll(dimTable.getColumnList());
                    DwColumn dwColumnKey = new DwColumn();
                    dwColumnKey.setName(dimTable.getDimPrimaryKey());
                    dwColumnList.add(dwColumnKey);

                    DwColumn dwColumnValue = new DwColumn();
                    dwColumnValue.setName(dimTable.getDimColumn());
                    dwColumnList.add(dwColumnValue);

                    boolean dimTableHasMasterColumn = this.hasFactColumnByDimTable(factTable.getMasterPrimaryKey(), dwColumnList);
                    if (!dimTableHasMasterColumn) {
                        continue;
                    }

                    String dimPrimaryKey = dimTable.getDimPrimaryKey();
                    //容错，如果事实表未指定关联主键，则默认取维度key构建。
                    if (null == masterPrimaryKey) {
                        masterPrimaryKey = dimPrimaryKey;
                        factTable.setMasterPrimaryKey(dimPrimaryKey);
                    }

                    boolean isMaster = masterPrimaryKey.equalsIgnoreCase(dimPrimaryKey) && masterPrimaryKey.equals(factTable.getFactColumn());
                    // 若维表定义了 dimColumnExpr（CASE 表达式），强制走 visitLeftJoin 以便 JOIN 维表并使用表达式分组
                    if (isMaster && dimTable.getDimColumnExpr() != null && !dimTable.getDimColumnExpr().isEmpty()) {
                        isMaster = false;
                    }
//                    dimension.setMaster(isMaster);
                    tuple.setMasterDimInFactTable(factTable.getTableName(), factTable.getFactColumn(), dimension.getCode(), isMaster);

                    boolean isLeftJoin = this.isLeftJoin(dimension, tuple) || tuple.isMemory();
                    //此处维度不是主维度或需要join操作（排序、筛选）的时候，走此逻辑。
                    if (!isMaster || isLeftJoin) {
//                        factTable.setMaster(true);
                        //事实表中不存在，获取其master维度，然后构建关联项。
                        Table masterDimTable = this.getRelationMasterFactTable(factTable, dimTable, dimension, tuple.getDimensionSet());
                        //如果主维度事实表与维度事实表一致，则join关系成立。
                        dimension.setRootJoin(true);
                        SingleFactTableSqlAgg singleFactTableSqlAgg = null;
                        if (null != masterDimTable) {
                            //次维度关联，需要先关联主维度。
                            dimension.setMaster(true);
                            singleFactTableSqlAgg = getRootTable(masterDimTable, rootMap, tuple);
                            this.visitLeftJoin(dimension, singleFactTableSqlAgg, masterDimTable, dimTable, tuple);
                            existsTable.add(masterDimTable.getTableName());

                        } else {

                            singleFactTableSqlAgg = getRootTable(factTable, rootMap, tuple);
                            //标准维度处理
                            this.visitLeftJoin(dimension, singleFactTableSqlAgg, factTable, dimTable, tuple);
                            existsTable.add(dimTable.getTableName());

                        }

                        if (null != singleFactTableSqlAgg) {
                            singleFactTableSqlAgg.getTableUseMap().put(factTable.getTableName(), dimension.getCode());
                        }

                    }
                }
            }
        }
    }

    private void replaceFirstLeftJoin(LeftJoinDimTable leftJoinDimTable, Dimension dim, Table factTable,
                                    String leftJoinFkId) {

        leftJoinDimTable.setDim(dim);
        leftJoinDimTable.setSelfId(leftJoinDimTable.getColumnId() != null ? leftJoinDimTable.getColumnId() : factTable.getMasterPrimaryKey());
        leftJoinDimTable.setFkId(leftJoinFkId);

    }

    private LeftJoinDimTable createLeftJoinDimTable(BuildSqlTuple tuple, Dimension dim, Table factTable,
                                                        Table dimTable, String columnId, String columnName,
                                                                String dimAlias, String leftJoinFkId) {

        String dimTableName = dimTable.getTableName();

        LeftJoinDimTable leftJoinedDimTable = new LeftJoinDimTable();
        leftJoinedDimTable.setTableName(dimTableName);
        leftJoinedDimTable.setAlias(dimAlias);
        leftJoinedDimTable.setSelfId(dimTable.getDimPrimaryKey() != null ? dimTable.getDimPrimaryKey() : factTable.getMasterPrimaryKey());
        leftJoinedDimTable.setSchema(dimTable.getSchemaName());
        leftJoinedDimTable.setTable(dimTable);

        leftJoinedDimTable.setFkId(leftJoinFkId);
        leftJoinedDimTable.setHasColumnDT(dimTable.getHasColumnDT());
        leftJoinedDimTable.setColumnId(columnId);
        leftJoinedDimTable.setColumnName(columnName);
        leftJoinedDimTable.setDim(dim);
        leftJoinedDimTable.setHierCode(dim.getHierCode());

        boolean isDegDim = leftJoinedDimTable.isDegDim();
        dim.setDegDim(isDegDim);

        boolean isSelfDim = leftJoinedDimTable.isSelfGroupId();
        dim.setSelfDim(isSelfDim);

        return leftJoinedDimTable;

    }

    private Dimension getBaseDimension(Dimension orgDim) {

        Dimension targetDim = orgDim;
        Set<Dimension> hasAllDimensionSet = orgDim.getHasAllDimensionSet();
        if (!CollectionUtils.isEmpty(hasAllDimensionSet)) {
            for (Dimension hasDim : hasAllDimensionSet) {
                Dimension hasTargetDim = this.getBaseDimension(hasDim);
                if (null != hasTargetDim) {
                    targetDim = hasTargetDim;
                    break;
                }
            }
        }

        return targetDim;

    }

    private LeftJoinDimTable findLeftJoinDimTable(SingleFactTableSqlAgg singleFactTableSqlAgg, Dimension orgDim, Table dimTable) {

        LeftJoinDimTable exist = null;
        List<LeftJoinDimTable> leftJoinedDimTableList = singleFactTableSqlAgg.getLeftJoinedDimTableList();

        //需要根据衍生维度获取下面所依赖的基本维度.
        Dimension targetDim = this.getBaseDimension(orgDim);

        String targetCode = targetDim.getCode();
        String targetTableName = dimTable.getTableName();
        String targetHierCode = targetDim.getHierCode();

        for (LeftJoinDimTable leftJoinDimTable : leftJoinedDimTableList) {

            leftJoinDimTable.setReplace(false);

            Dimension leftDim = leftJoinDimTable.getDim();
            String leftDimCode = leftDim.getCode();
            String tableName = leftJoinDimTable.getTableName();
            String hierCode = leftJoinDimTable.getHierCode();

            //如果是同一维度则直接应对
            if (targetCode.equalsIgnoreCase(leftDimCode)) {
                exist = leftJoinDimTable;
                break;
            } else if (targetTableName.equalsIgnoreCase(tableName) && targetHierCode.equalsIgnoreCase(hierCode)) {

                Level orgLevel = orgDim.getLevel();
                Integer orgIdx = Integer.valueOf(0);
                if (null != orgLevel) {
                    orgIdx = orgLevel.getSequence();
                }

                Level leftLevel = leftDim.getLevel();
                Integer leftDimIdx = Integer.valueOf(0);
                if (null != leftLevel) {
                    leftDimIdx = leftLevel.getSequence();
                }

                leftJoinDimTable.setReplace(orgIdx > leftDimIdx);

                exist = leftJoinDimTable;
                break;

            }

        }

        return exist;

    }

    private void visitLeftJoin(Dimension dim, SingleFactTableSqlAgg singleFactTableSqlAgg, Table factTable, Table dimTable, BuildSqlTuple tuple) {

        String dimTableName = dimTable.getTableName();
        String fromTable = singleFactTableSqlAgg.getFrom();

        boolean isMaster = dim.isMaster();

        String dimPrimaryKey = dimTable.getDimPrimaryKey();
        if (isMaster) {
            dimTable.setMasterPrimaryKey(dimPrimaryKey);
        }

        String columnId = dimTable.getDimPrimaryKey();
        String columnName = dimTable.getDimColumn();

        String from = fromTable;
        singleFactTableSqlAgg.setFrom(from);

        String dimAlias = null;
        String leftJoinFkId = null;

        DimType dimType = dim.getDimType();
        LeftJoinDimTable leftJoinedDimTable = this.findLeftJoinDimTable(singleFactTableSqlAgg, dim, dimTable);
        //找到join维表
        if (null == leftJoinedDimTable || DimType.STD_WITHOUT_TABLE.equals(dimType)) {
            //找不到则新创建一个
            dimAlias = IndicatorConstant.ALIAS + tuple.getIdx();
            tuple.increment();
            leftJoinFkId = factTable.getFactColumn();

            leftJoinedDimTable = this.createLeftJoinDimTable(tuple, dim, factTable, dimTable, columnId, columnName, dimAlias, leftJoinFkId);
            singleFactTableSqlAgg.getLeftJoinedDimTableList().add(leftJoinedDimTable);

        } else {

            if (leftJoinedDimTable.isReplace()) {
                leftJoinFkId = factTable.getFactColumn();
                this.replaceFirstLeftJoin(leftJoinedDimTable, dim, factTable, leftJoinFkId);
            }

            //找到可用维度表后，需要获取别名和关联外键。
            dimAlias = leftJoinedDimTable.getAlias();
            leftJoinFkId = leftJoinedDimTable.getFkId();
        }

        boolean isDegDim = leftJoinedDimTable.isDegDim();
        dim.setDegDim(isDegDim);

        boolean isSelfDim = leftJoinedDimTable.isSelfGroupId();
        dim.setSelfDim(isSelfDim);

        if (isSelfDim) {

            dimAlias = singleFactTableSqlAgg.getAlias();
            columnId = factTable.getFactColumn();
            columnName = factTable.getFactColumn();
            dimPrimaryKey = factTable.getFactColumn();

        }

        // 若 dimColumnExpr 已设置，则用 CASE 表达式（替换 {d} 为维表别名）作为 GROUP BY 键
        String dimColumnExpr = dimTable.getDimColumnExpr();
        String column;
        boolean hasDimColumnExpr = dimColumnExpr != null && !dimColumnExpr.isEmpty() && !isSelfDim;
        if (hasDimColumnExpr) {
            column = dimColumnExpr.replace("{d}", dimAlias);
        } else {
            column = this.getColumn(dimAlias, columnId, dim);
        }

        String dimColumnName = "#q#" + getColumnAlias(dim) + "ID#q#";
        String dimId = column + " as " + dimColumnName;

        this.addColumnGroupBy(singleFactTableSqlAgg, tuple, dimId, column, dimColumnName, dim, dimTable, dimAlias, columnId);

        //维度code和列名的映射。
        tuple.getDimCodeColumnMap().put(dim.getCode(), column);
        singleFactTableSqlAgg.getDimensionSet().add(dim);

        //设置where
        if (this.isDateViewType(dim.getViewType())) {
            //日期类型按日进行查询
            String alias = singleFactTableSqlAgg.getAlias();
            this.visitWhere(singleFactTableSqlAgg, dim, tuple, alias, leftJoinFkId, leftJoinFkId);
        } else {
            String filterColumn = hasDimColumnExpr ? column : columnId;
            this.visitWhere(singleFactTableSqlAgg, dim, tuple, dimAlias, filterColumn, filterColumn);
        }

    }

    private String findLevelFk(BuildSqlTuple tuple, Dimension dim, Table factTable) {

        String fkColumn = factTable.getFactColumn();

        Set<Dimension> dimensionSet = tuple.getDimensionSet();
        for (Dimension dimension : dimensionSet) {

            if (dim.getHierCode().equalsIgnoreCase(dimension.getHierCode())
                    && !dim.getCode().equalsIgnoreCase(dimension.getCode())
                        && dimension.getLevel().getSequence() > dim.getLevel().getSequence()) {

//                String columnFkId = this.getDimFkId(dimension.getCode(), measure, factTable.getTableName(), tuple.getDimensionSet());
                Table orgTable = this.findFactTable(factTable.getTableName(), dimension);
                if (null != orgTable) {
                    fkColumn = orgTable.getFactColumn();
                }

            }

        }

        return fkColumn;

    }

    private Table findFactTable(String name, Dimension dim) {
        List<Table> factTableSet = dim.getFactTableList();
        for (Table factTable : factTableSet) {
            if (name.equalsIgnoreCase(factTable.getTableName())) {
                return factTable;
            }
        }
        return null;
    }

    private void visitWhere(SingleFactTableSqlAgg singleFactTableSqlAgg, Dimension dimension, BuildSqlTuple tuple, String alias, String column, String columnId) {

        QueryParam queryParam = tuple.getQueryParam();
        if (null != queryParam) {

            List<Filter> filterList = queryParam.getFilterList();

            filterList.forEach(filter -> {

                if (filter.getCode().equals(dimension.getCode())
                    && CollectionUtils.isEmpty(filter.getAuthElementMeasureSet())) {

                    Filter sqlFilter = new Filter();
                    sqlFilter.setCode(filter.getCode());
                    sqlFilter.setAlias(alias);
                    sqlFilter.setDimType(dimension.getDimType());
                    sqlFilter.setColumnId(columnId);
                    sqlFilter.setColumn(column);

                    sqlFilter.setViewType(dimension.getViewType());
                    sqlFilter.getOperatorList().addAll(filter.getOperatorList());
                    sqlFilter = reBuild(sqlFilter);
                    singleFactTableSqlAgg.getWhereFilterList().add(sqlFilter);

                    //筛选器使用过
                    filter.setUse(true);

                } else if (!CollectionUtils.isEmpty(filter.getAuthElementMeasureSet())) {
                    //筛选器使用过
                    filter.setUse(true);
                }
            });

        }

    }

    private static Filter reBuild(Filter sqlFilter) {

        if (ViewType.DAY.equals(sqlFilter.getViewType()) && "date_key".equalsIgnoreCase(sqlFilter.getOrgColumn())) {
            sqlFilter.setColumn(sqlFilter.getColumnId());
            sqlFilter.setOrgColumn(sqlFilter.getColumnId());
        }

        return sqlFilter;

    }

    public static boolean isDateViewType(ViewType viewType) {

        boolean isDateViewType = ViewType.DAY.equals(viewType) ||
                ViewType.WEEK.equals(viewType) ||
                ViewType.MONTH.equals(viewType) ||
                ViewType.SEASON.equals(viewType) ||
                ViewType.YEAR.equals(viewType);

        return isDateViewType;

    }

    private void addColumnGroupBy(SingleFactTableSqlAgg singleFactTableSqlAgg, BuildSqlTuple tuple, String dimColumnId, String groupByColumn, String columnName, Dimension dim) {
        this.addColumnGroupBy(singleFactTableSqlAgg, tuple, dimColumnId, groupByColumn, columnName, dim, null, null, null);
    }

    private void addColumnGroupBy(SingleFactTableSqlAgg singleFactTableSqlAgg, BuildSqlTuple tuple, String dimColumnId, String groupByColumn, String columnName, Dimension dim, Table dimTable, String dimAlias, String columnId) {

        Set<String> displayedDimCodeSet = tuple.getDisplayDimensionCodeSet();
        if (!dim.isAll() && displayedDimCodeSet.contains(dim.getCode())) {

            List<GroupColumn> groupColumnList = dim.getGroupColumnList();
            if (!CollectionUtils.isEmpty(groupColumnList)) {
                String derivedDimGroupByColumn = "case";
                for (GroupColumn groupColumn : groupColumnList) {

                    derivedDimGroupByColumn += " when ";

                    List<Filter> filterList = groupColumn.getFilterList();
                    String column = "";
                    for (Filter filter : filterList) {

                        //衍生维度不能设置filter上的code
                        if (CollectionUtils.isEmpty(dim.getHasAllDimensionSet())) {
                            filter.setCode(dim.getCode());
                        }

                        filter.setAlias(dimAlias);
                        filter.setColumn(columnId);
                        derivedDimGroupByColumn += this.buildFilterSql(filter, singleFactTableSqlAgg, tuple, true);
                    }

                    derivedDimGroupByColumn += " then '";
                    derivedDimGroupByColumn += groupColumn.getName();
                    derivedDimGroupByColumn += "'";
                }

                derivedDimGroupByColumn += " else " + groupByColumn + " end";
                dimColumnId = derivedDimGroupByColumn + " as " + columnName;
                groupByColumn = derivedDimGroupByColumn;

            }

//            singleFactTableSqlAgg.getColumnList().add(dimColumnId);
            singleFactTableSqlAgg.getGroupDimSet().add(dim);

            this.addOnlyValue(singleFactTableSqlAgg.getColumnList(), dimColumnId);
            this.addOnlyValue(singleFactTableSqlAgg.getGroupByList(), groupByColumn);

            this.addOnlyValue(singleFactTableSqlAgg.getDimColumnNameList(), columnName);
            this.addOnlyValue(singleFactTableSqlAgg.getDimColumnList(), dimColumnId);

        }

    }

    private void addOnlyValue(List<String> list, String obj) {

        if (!list.contains(obj)) {
            list.add(obj);
        }

    }

    public static String getColumnAlias(Dimension dimension) {
        return "_d_alis_" + dimension.getName().replaceAll("-", "");
    }

    public static String getColumnAlias(Measure measure) {
        String name = measure.getName();
        return "_m_alis_" + name.replaceAll("-", "");
    }

    public static String getMeasureAlias(Measure measure) {
        String name = measure.getName();
        String alias = measure.getAlias();
        if (StringUtil.isNotEmpty(alias) && !CollectionUtils.isEmpty(measure.getRatioList())) {
            name = alias;
        }
        return "_m_alis_" + name.replaceAll("-", "");
    }

    private String getColumn(String dimAlias, String dimColumnId, Dimension dim) {

        String resultColumn = null;
        if (ViewType.WEEK.equals(dim.getViewType())) {
//            String column = dimAlias + "." + dimColumnId;
            String column = setAlias(dimColumnId, dimAlias);
            resultColumn = column;
        } else if (ViewType.MONTH.equals(dim.getViewType())) {
//            String column = dimAlias + "." + dimColumnId;
            String column = setAlias(dimColumnId, dimAlias);
            resultColumn = column;

        } else {
//            resultColumn = dimAlias + "." + dimColumnId;
            resultColumn = setAlias(dimColumnId, dimAlias);
        }

        return resultColumn;

    }

    /**
     * 获取维度
     * @param factTable
     * @param dimTable
     * @param dimension
     * @param dimensionSet
     * @return
     */
    private Table getRelationMasterFactTable(Table factTable, Table dimTable, Dimension dimension, Set<Dimension> dimensionSet) {

        String dimHierCode = this.getHierarchyCode(dimension);
        if ("Null".equalsIgnoreCase(dimHierCode)) {
            return null;
        }
        //维度表集合
        for (Dimension masterDim : dimensionSet) {
            String masterHierCode = this.getHierarchyCode(masterDim);
            //排除自身
            if (!masterDim.getCode().equalsIgnoreCase(dimension.getCode())) {
                List<Table> masterDimTableList = masterDim.getDimTableList();
                //主维度维度表集合
                for (Table masterDimTable : masterDimTableList) {
                    //判断是否是主维度  条件：1.维度表一致；2.主维度level为1；3.主维度也含有当前事实表
                    if (masterDimTable.getTableName().equalsIgnoreCase(dimTable.getTableName())) {
                        List<Table> masterFactTableList = masterDim.getFactTableList();
                        for (Table masterFactTable : masterFactTableList) {
                            if (masterFactTable.isMaster() && masterFactTable.getTableName().equalsIgnoreCase(factTable.getTableName()) && dimHierCode.equalsIgnoreCase(masterHierCode)) {
                                masterDim.setRootJoin(true);
                                return masterFactTable;
                            }
                        }
                    }
                }
            }
        }

        return null;

    }

    private String getHierarchyCode(Dimension dim) {

        String code = "Null";
        Level level = dim.getLevel();
        if (null != level ) {
            String levelHierCode = level.getHierarchyCode();
            if (null != levelHierCode) {
                code = levelHierCode;
            }
        }

        return code;

    }

    private void getAllFilter(Measure measure, List<Filter> filterList) {
        List<Table> factTableList = measure.getFactTable();
        for (Table table : factTableList) {
            List<Filter> measFilterList = table.getFilterList();
            filterList.addAll(measFilterList);
        }

        Set<Measure> hasAllMeasureSet = measure.getHasAllMeasureSet();
        for (Measure sonMeasure : hasAllMeasureSet) {
            this.getAllFilter(sonMeasure, filterList);
        }
    }

    /**
     * 是否需要按名称查询
     * @param dimension
     * @param tuple
     * @return
     */
    private boolean isOprMatching(Dimension dimension, BuildSqlTuple tuple) {

        boolean isOprMatching = false;
        QueryParam queryParam = tuple.getQueryParam();
        String code = dimension.getCode();
        List<Filter> filterList = new ArrayList<Filter>();

        if (null != queryParam) {
            filterList.addAll(queryParam.getFilterList());
        }

        Set<Measure> measureSet = tuple.getMeasureSet();
        for (Measure measure : measureSet) {
           this.getAllFilter(measure, filterList);
        }

        if (null != filterList && filterList.size() > 0) {
            for (Filter filter : filterList) {

                if (!code.equalsIgnoreCase(filter.getCode())) {
                    continue;
                }

                List<Operator> operatorList = filter.getOperatorList();
                for (Operator operator : operatorList) {

                    SqlOprType sqlOprType = operator.getSqlOprType();
                    if (SqlOprType.LIKE.equals(sqlOprType)) {
                        isOprMatching = true;
                    } else if (SqlOprType.LIKE_NO_INCLUDE.equals(sqlOprType)) {
                        isOprMatching = true;
                    } else if (SqlOprType.EQUAL_NULL.equals(sqlOprType)) {
                        isOprMatching = true;
                    } else if (SqlOprType.EQUAL_NO_NULL.equals(sqlOprType)) {
                        isOprMatching = true;
                    } else if (SqlOprType.EQUAL_NULL_CHART.equals(sqlOprType)) {
                        isOprMatching = true;
                    } else if (SqlOprType.EQUAL_NO_NULL_CHART.equals(sqlOprType)) {
                        isOprMatching = true;
                    }
                }
            }
        }

        return isOprMatching;

    }

    private boolean isLeftJoin(Dimension dimension, BuildSqlTuple tuple) {

        boolean isOrder = this.isOrderValues(dimension, tuple);
        boolean isOprMatching = this.isOprMatching(dimension, tuple);
        boolean isExtendedJoin = dimension.isExtended() && !dimension.isMaster();
        boolean isMeasFactColumn = this.isMeasFactColumn(tuple, dimension);

        return isOrder || isOprMatching || isExtendedJoin || isMeasFactColumn;

    }

    private boolean isMeasFactColumn(BuildSqlTuple tuple, Dimension dim) {

        boolean isReplaceColumn = false;
        Set<Measure> measureSet = tuple.getMeasureSet();

        for (Measure measure : measureSet) {

            List<DimMeasTableColumn> dimMeasTableColumnList = measure.getDimMeasTableColumnList();
            if (!CollectionUtils.isEmpty(dimMeasTableColumnList)) {
                for (DimMeasTableColumn dimMeasTableColumn : dimMeasTableColumnList) {
                    if (dim.getCode().equalsIgnoreCase(dimMeasTableColumn.getDimCode())) {
                        isReplaceColumn = true;
                        break;
                    }

                }
            }

            if (isReplaceColumn) {
                break;
            }

        }

        return isReplaceColumn;
    }

    /**
     * 是否含有维度排序
     * @param dimension
     * @param tuple
     * @return
     */
    private boolean isOrderValues(Dimension dimension, BuildSqlTuple tuple) {
        boolean isOrder = false;
        QueryParam queryParam = tuple.getQueryParam();
        if (null != queryParam) {
            List<Order> orderList = queryParam.getOrderList();
            if (null != orderList && orderList.size() > 0) {
                for (Order order : orderList) {

                    if (order.getCode().equals(dimension.getCode())) {
                        isOrder = true;
                        break;
                    }
                }
            }
        }

        return isOrder;

    }

    /**
     * 该表已经存在与已知的交集事实表中
     *
     * @param rootMap
     * @param table
     * @return
     */
    public boolean isHasTable(Map<String, List<SingleFactTableSqlAgg>> rootMap, String table) {

        if (null == table) {
            return false;
        }

        for (Map.Entry<String, List<SingleFactTableSqlAgg>> entry : rootMap.entrySet()) {
            List<SingleFactTableSqlAgg> singleFactTableSqlAggList = entry.getValue();
            for (SingleFactTableSqlAgg singleFactTableSqlAgg : singleFactTableSqlAggList) {
                if (table.equalsIgnoreCase(singleFactTableSqlAgg.getName())) {
                    return true;
                }
            }
        }

        return false;

    }

    /**
     * 根据已经可用的事实表，构建、获取rootTable.
     * @param factTable
     * @param rootMap
     */
    private SingleFactTableSqlAgg getRootTable(Table factTable, Map<String, List<SingleFactTableSqlAgg>> rootMap, BuildSqlTuple tuple) {

        //此处如果是多源
        SourceType sourceType = factTable.getSourceType();
        String source = sourceType.getDesc();
        String table = factTable.getTableName();

        List<SingleFactTableSqlAgg> singleFactTableSqlAggs = null;

        SingleFactTableSqlAgg singleFactTableSqlAgg = null;
        if (!rootMap.containsKey(source)) {

            singleFactTableSqlAggs = new LinkedList<>();
            singleFactTableSqlAgg = new SingleFactTableSqlAgg();

            rootMap.put(source, singleFactTableSqlAggs);

        }

        singleFactTableSqlAggs = rootMap.get(source);
        singleFactTableSqlAgg = findTable(singleFactTableSqlAggs, table);
        String rootTableAlias;

        if (null == singleFactTableSqlAgg) {

            singleFactTableSqlAgg = new SingleFactTableSqlAgg();
            singleFactTableSqlAggs.add(singleFactTableSqlAgg);

            rootTableAlias = IndicatorConstant.ALIAS + tuple.getIdx();
            tuple.increment();

            singleFactTableSqlAgg.setName(table);
            singleFactTableSqlAgg.setAlias(rootTableAlias);
            singleFactTableSqlAgg.setFrom(table + " as " + rootTableAlias);
            singleFactTableSqlAgg.setSource(sourceType);
            singleFactTableSqlAgg.setSchema(factTable.getSchemaName());
            singleFactTableSqlAgg.setStatsAllColumnList(tuple.getStatsAllColumnList());
            singleFactTableSqlAgg.setTable(factTable);

        }

        return singleFactTableSqlAgg;

    }

    private SingleFactTableSqlAgg findTable(List<SingleFactTableSqlAgg> singleFactTableSqlAggs, String tableName) {

        SingleFactTableSqlAgg result = null;
        for (SingleFactTableSqlAgg singleFactTableSqlAgg : singleFactTableSqlAggs) {
            if (singleFactTableSqlAgg.getName().equals(tableName)) {
                result = singleFactTableSqlAgg;
                break;
            }
        }

        return result;

    }

    /**
     * 判断是否为可用事实表，一期采用所含维度都存在即为可用。后续根据产品要求调整。
     * @param table
     * @param dimensionSet
     * @param measure
     * @param orgDimCodeSet
     * @param measTableList
     * @return
     */
    private boolean hasAllDim(Table table, Set<Dimension> dimensionSet, Measure measure, Set<String> orgDimCodeSet, List<Table> measTableList) {

        Set<String> hasDimCodeSet = new LinkedHashSet<>();
        boolean isExists = false;

        if (dimensionSet.size() == 0 || orgDimCodeSet.size() == 0) {
            isExists = true;
            return isExists;
        }

        for (Dimension dim : dimensionSet) {

            if (!orgDimCodeSet.contains(dim.getCode())) {
                hasDimCodeSet.add(dim.getCode());
                continue;
            }

            isExists = false;
            List<Table> tableList = dim.getFactTableList();
            for (Table dimFactTable : tableList) {
                String tableName = table.getTableName();
                if (null != tableName && tableName.equalsIgnoreCase(dimFactTable.getTableName())) {
                    isExists = true;
                    break;
                }
            }
            if (!isExists) {
//                LOG.error(" error:" + measure.getMeasName() + ":" + measure.getCode() + " tableName:" + table.getTableName() + " dimCode:" + dim.getDimCode() + "  dimName:" + dim.getDimName());
                break;
            } else {
                hasDimCodeSet.add(dim.getCode());
            }

        }

        if (!isExists) {
            return isExists;
        }

        for (Table measTable : measTableList) {

            String tableName = table.getTableName();
            if (null != tableName && tableName.equalsIgnoreCase(measTable.getTableName())) {
                continue;
            }

            Set<String> otherDimCodeSet = new LinkedHashSet<>();
            for (Dimension dim : dimensionSet) {

                List<Table> tableList = dim.getFactTableList();
                for (Table dimFactTable : tableList) {
                    String otherTableName = measTable.getTableName();
                    if (null != otherTableName && otherTableName.equalsIgnoreCase(dimFactTable.getTableName())) {
                        otherDimCodeSet.add(dim.getCode());
                        break;
                    }
                }

            }

            if (otherDimCodeSet.size() > hasDimCodeSet.size() && this.isHasAll(hasDimCodeSet, otherDimCodeSet)) {
                isExists = false;
            }

        }

        return isExists;

    }

    private boolean isHasAll(Set<String> codeSet, Set<String> otherCodeSet) {

        boolean isHasAll = true;
        for (String code : codeSet) {
            if (!otherCodeSet.contains(code)) {
                isHasAll = false;
                break;
            }
        }

        return isHasAll;

    }


    @Override
    public String buildFullJoinGroupSql(BuildSqlTuple tuple) {
        String fullJoinSql = this.buildFullJoinGroupSql(tuple, false);
        tuple.setFullJoinGroupSql(fullJoinSql);

        String fullJoinTempSql = this.buildFullJoinGroupSql(tuple, true);
        tuple.setFullJoinTempGroupSql(fullJoinTempSql);

        return fullJoinSql;
    }

    private String buildCaseWhen(String measFun) {
//       return "(case when " + measFun + " is null then null else " + measFun + " end) ";
        return measFun;
    }

    private String buildFullColumnBaseMeasure(Measure measure, String alias, StringBuffer exCodeBuffer, BuildSqlTuple tuple) {

        String measureColumn = "";
        String exCode = exCodeBuffer.toString();
        if (StringUtil.isEmpty(exCode)) {
            String measFun = "sum(" + alias + ".#q#" + getColumnAlias(measure) + "#q#)";
            measFun = this.buildCaseWhen(measFun);
            measureColumn = ", " + measFun + " as #q#" + getColumnAlias(measure) + "#q#";
        } else {
            String measFun = "sum(" + alias + ".#q#ex" + getColumnAlias(measure) + exCode + "#q#)";
            measFun = this.buildCaseWhen(measFun);
            measureColumn = ", " + measFun + " as #q#ex" + getColumnAlias(measure) + exCode + "#q#";

        }

        return measureColumn;
    }

    private String buildFullColumnMeasure(Measure measure, String alias, StringBuffer exCodeBuffer, BuildSqlTuple tuple) {

        Set<String> useTableSet = tuple.getUseTableSet();
        String measureColumn = "";
        for (Table factTable : measure.getFactTable()) {

            String tableName = factTable.getTableName();
            MeasureType measureType = factTable.getApplyType();
            if (useTableSet.contains(tableName)
                    || MeasureType.DERIVED.equals(measureType)
                    || MeasureType.EXTENDED.equals(measureType)) {
                //full join 类型，查询基础指标。
                if (MeasureType.ORIGIN.equals(factTable.getApplyType())) {
                    measureColumn += this.buildFullColumnBaseMeasure(measure, alias, exCodeBuffer, tuple);
                } else if (MeasureType.DERIVED.equals(measureType)) {

                    exCodeBuffer.append("_").append(measure.getCode());
                    Set<Measure> allMeasureSet = measure.getHasAllMeasureSet();

                    for (Measure hasMeasure : allMeasureSet) {

                        if (!isSonMeasure(measure, hasMeasure.getCode())) {
                            continue;
                        }

                        StringBuffer sonExCodeBuffer = new StringBuffer(exCodeBuffer);
                        measureColumn += this.buildFullColumnMeasure(hasMeasure, alias, sonExCodeBuffer, tuple);
                    }

                } else if (MeasureType.EXTENDED.equals(measureType)) {

                    exCodeBuffer.append("_").append(measure.getCode());
                    Set<Measure> allMeasureSet = measure.getHasAllMeasureSet();

                    for (Measure hasMeasure : allMeasureSet) {

                        if (!isSonMeasure(measure, hasMeasure.getCode())) {
                            continue;
                        }

                        StringBuffer sonExCodeBuffer = new StringBuffer(exCodeBuffer);
                        measureColumn += this.buildFullColumnMeasure(hasMeasure, alias, sonExCodeBuffer, tuple);
                    }

                }
                break;
            }
        }

        return measureColumn;

    }

    public String buildFullJoinGroupSql(BuildSqlTuple tuple, boolean isTemp) {

        List<String> rootSqls = tuple.getSingleFactTableQuerySqlMap().values().stream().flatMap(c -> {
            return c.stream();
        }).collect(Collectors.toList());
        String alias = "t_F_G_J";
        Set<Dimension> dimensionSet = tuple.getDimensionSet();

        String column = this.getColumnDimension(tuple.getDimensionSet(), tuple.getRootTableMap(), alias, tuple.getDisplayDimensionCodeSet());
        String measureColumn = "";
        Set<Measure> measureSet = tuple.getUseMeasureSet();
//        Set<Measure> measureSet = tuple.getChoiceMeasureSet();
        Set<String> useTableSet = tuple.getUseTableSet();
        for (Measure measure : measureSet) {
            measureColumn += this.buildFullColumnMeasure(measure, alias, new StringBuffer(), tuple);
        }

        String from = null;
        if (isTemp) {
            from = "(select * from \"TEMP\".\"TEMP_TABLE\") as " + alias;
        } else {
            String unionSql = "";
            for (int i = 0; i < rootSqls.size(); i++) {
                if (i > 0) {
                    unionSql += " union all ";
                }
                unionSql += "(" + rootSqls.get(i) + ")";
            }
            from = "(" + unionSql + ") as " + alias;
        }

        //order by 处理
        String orderBy = this.buildOrderBy(tuple, alias);
        String rankSql = "";
        if (!tuple.isMultipleNesting()) {
            if (!isTemp) {
                String overBy = "";
                if (orderBy.length() > 0) {
                    overBy = " order by " + orderBy;
                }
//                rankSql = ", row_number() over (" + overBy + ") as rank";
            } else {
                rankSql = "";
            }
        }

        String selectSql = column + rankSql + measureColumn;
        if (tuple.hasSubtotal() || tuple.hasRowColSubtotal()) {

            if (tuple.isPivot()) {
                column = this.buildGroupingSets(tuple, column, alias, tuple.getDisplayDimensionCodeSet());
            } else if (tuple.isTable()) {
//                column = this.buildGroupingSetsByTable(tuple, column, alias, tuple.getDisplayDimensionCodeSet());
                column = this.buildCubesByTable(tuple, column, alias, tuple.getDisplayDimensionCodeSet());
            }

            /*
            GROUPING SETS(
                    ( t_F_G_J.`_d_alis_车型id`, t_F_G_J.`_d_alis_VOC创建月id` ),
                    ( t_F_G_J.`_d_alis_VOC创建月id`  )
                )
             */
        }

        String groupBySql = " group by " + column;

        String orderBySql = "";
        if (!tuple.isMultipleNesting()) {
            if (orderBy.length() > 0) {
                orderBySql = " order by " + orderBy;
            }
        }

        if (tuple.isNullColumn()) {
            selectSql = selectSql.replaceFirst(",", "");
            groupBySql = "";
            orderBySql = "";
        }

        String sql = "select " + selectSql + " from " + from + groupBySql + orderBySql;
        String dorisSql = QueryExecutorService.formatSql(SourceType.MYSQL, sql);
        return sql;

    }

    /**
     * 获取到右轴上的所有维度名称
     * @param rowAxisSet
     * @param alias
     * @param tuple
     * @param orgDimCodeSet
     * @return
     */
    private String getRightAxisDimColumn(Set<BaseConfigure> axisSet, String alias, BuildSqlTuple tuple, Set<String> orgDimCodeSet, String dimCode) {

        String column = "";
        boolean start = false;
        for (BaseConfigure config : axisSet) {

            Dimension dim = this.findDimension(config.getCode(), tuple.getDimensionSet());
            if (null == dim) {
                continue;
            }

            if (null != dimCode && dimCode.equalsIgnoreCase(dim.getCode())) {
                start = true;
                continue;
            }

            DimType dimType = dim.getDimType();

            if (!orgDimCodeSet.contains(dim.getCode()) || !start) {
                continue;
            }

            //标准维度
            if (DimType.STD_WITHOUT_TABLE.equals(dimType) || DimType.STD_WITH_TABLE.equals(dimType)) {
                column += ", " + alias + ".#q#" + getColumnAlias(dim) + "id#q#";
            } else if (DimType.DEGENERATE_DIM.equals(dimType) || DimType.CUSTOM.equals(dimType)) {
                //退化维
                column += ", " + alias + ".#q#" + getColumnAlias(dim) + "#q#";
            }

        }

        column = column.replaceFirst(", ", "");

        return column;

    }

    /**
     * 获取到左轴上的所有维度名称
     * @param rowAxisSet
     * @param alias
     * @param tuple
     * @param orgDimCodeSet
     * @return
     */
    private String getAxisDimColumn(Set<BaseConfigure> axisSet, String alias, BuildSqlTuple tuple, Set<String> orgDimCodeSet, String dimCode) {

        String column = "";
        for (BaseConfigure config : axisSet) {

            Dimension dim = this.findDimension(config.getCode(), tuple.getDimensionSet());
            if (null == dim) {
                continue;
            }

            DimType dimType = dim.getDimType();

            if (!orgDimCodeSet.contains(dim.getCode())) {
                continue;
            }
            //标准维度
            if (DimType.STD_WITHOUT_TABLE.equals(dimType) || DimType.STD_WITH_TABLE.equals(dimType)) {
                column += ", " + alias + ".#q#" + getColumnAlias(dim) + "id#q#";
            } else if (DimType.DEGENERATE_DIM.equals(dimType) || DimType.CUSTOM.equals(dimType)) {
                //退化维
                column += ", " + alias + ".#q#" + getColumnAlias(dim) + "#q#";
            }

            if (null != dimCode && dimCode.equalsIgnoreCase(dim.getCode())) {
                break;
            }

        }

        column = column.replaceFirst(", ", "");

        return column;

    }

    private String buildCubesByTable(BuildSqlTuple tuple, String allColumn, String alias, Set<String> orgDimCodeSet) {

        Set<String> groupColumns = new HashSet<>();
        Set<BaseConfigure> allDimSet = tuple.getRowAxisSet();
        String group = "rollup(" + allColumn + ")";

        String groupingSets = group;
        return groupingSets;

    }

    private String buildGroupingSetsByTable(BuildSqlTuple tuple, String allColumn, String alias, Set<String> orgDimCodeSet) {

        Set<String> groupColumns = new HashSet<>();
        Set<BaseConfigure> allDimSet = tuple.getRowAxisSet();
        String group = "(" + allColumn + ")";
        groupColumns.add(allColumn);

        int dimCount = 0;
        for (BaseConfigure config : allDimSet) {

            String dimCode = config.getCode();
            Dimension dim = this.findDimension(dimCode, tuple.getDimensionSet());
            if (null == dim) {
                continue;
            }

            dimCount++;

        }

        if (dimCount == 1) {
            // MySQL 不支持 GROUPING SETS，用 WITH ROLLUP 替代（单维度时语义完全等价）
            return allColumn + " WITH ROLLUP";
        }

        for (BaseConfigure config : allDimSet) {

            String dimCode = config.getCode();
            Dimension dim = this.findDimension(dimCode, tuple.getDimensionSet());
            if (null == dim || !config.getHasSubtotal()) {
                continue;
            }

            String leftColumn = this.getAxisDimColumn(allDimSet, alias, tuple, orgDimCodeSet, dimCode);
            if (!hasGroupSet(groupColumns, leftColumn)) {
                group += ", (" + leftColumn + ")";
                groupColumns.add(leftColumn);
            }


        }

        // MySQL 不支持 GROUPING SETS，多维度时降级为普通 GROUP BY
        return allColumn;

    }

    private boolean hasGroupSet(Set<String> groups, String column) {

        List<String> cols = Arrays.asList(column.split(","));

        for (String group : groups) {

            List<String> gCols = Arrays.asList(group.split(","));

            if (cols.size() == gCols.size()) {

                Boolean has = true;
                for (String gCol : gCols) {

                    Boolean use = false;
                    for (String col : cols) {
                        col = col.replaceAll("\\.", "").replaceAll("#", "").trim();
                        gCol = gCol.replaceAll("\\.", "").replaceAll("#", "").trim();
                        if (col.equals(gCol)) {
                            use = true;
                            break;
                        }
                    }
                    if (!use) {
                        has = false;
                        break;
                    }
                }

                if (has) {
                    return has;
                }

            }

        }

        return false;

    }

    private String buildGroupingSets(BuildSqlTuple tuple, String allColumn, String alias, Set<String> orgDimCodeSet) {

        QueryParam queryParam = tuple.getQueryParam();
        Boolean isRowSum = queryParam.isRowSum();
        Boolean isColSum = queryParam.isColSum();

        Set<BaseConfigure> rowAxisSet = tuple.getRowAxisSet();
        Set<BaseConfigure> columnAxisSet = tuple.getColumnAxisSet();
        String group = "(" + allColumn + ")";

        int dimCount = 0;
        for (BaseConfigure config : rowAxisSet) {

            String dimCode = config.getCode();
            Dimension dim = this.findDimension(dimCode, tuple.getDimensionSet());
            if (null == dim) {
                continue;
            }

            dimCount++;

        }

        for (BaseConfigure config : columnAxisSet) {

            String dimCode = config.getCode();
            Dimension dim = this.findDimension(dimCode, tuple.getDimensionSet());
            if (null == dim) {
                continue;
            }

            dimCount++;

        }

        if (dimCount == 1) {
            // MySQL 不支持 GROUPING SETS，用 WITH ROLLUP 替代（单维度时语义完全等价）
            return allColumn + " WITH ROLLUP";
        }

        if (rowAxisSet.size() > 1) {

            BaseConfigure[] baseConfigures = rowAxisSet.toArray(new BaseConfigure[rowAxisSet.size()]);
            for (int i = 0; i < baseConfigures.length; i++) {

                BaseConfigure config = baseConfigures[i];

                String dimCode = config.getCode();
                Dimension dim = this.findDimension(dimCode, tuple.getDimensionSet());
                if (null == dim) {
                    continue;
                }

                String otherAxis = this.getAxisDimColumn(columnAxisSet, alias, tuple, orgDimCodeSet, null);
                String coluns = "," + otherAxis;
                group += ", (" + coluns + ")";

                break;

            }
        }

        if (columnAxisSet.size() > 1) {

            BaseConfigure[] baseConfigures = columnAxisSet.toArray(new BaseConfigure[columnAxisSet.size()]);
            for (int i = 0; i < baseConfigures.length; i++) {

                BaseConfigure config = baseConfigures[i];

                String dimCode = config.getCode();
                Dimension dim = this.findDimension(dimCode, tuple.getDimensionSet());
                if (null == dim) {
                    continue;
                }

                String rightOrTopColumn = this.getRightAxisDimColumn(columnAxisSet, alias, tuple, orgDimCodeSet, dimCode);
                String otherAxis = this.getAxisDimColumn(rowAxisSet, alias, tuple, orgDimCodeSet, null);
                String coluns = "," + otherAxis;
                group += ", (" + coluns + ")";

                break;

            }
        }

        /*
        for (BaseConfigure config : rowAxisSet) {

            String dimCode = config.getCode();
            Dimension dim = this.findDimension(dimCode, tuple.getDimensionSet());
            if (null == dim || !config.getHasSubtotal()) {
                continue;
            }

            String leftOrTopColumn = this.getAxisDimColumn(rowAxisSet, alias, tuple, orgDimCodeSet, dimCode);
            group += ", (" + leftOrTopColumn + ")";
            String otherAxis = this.getAxisDimColumn(columnAxisSet, alias, tuple, orgDimCodeSet, null);
            String coluns = leftOrTopColumn + "," + otherAxis;
            group += ", (" + coluns + ")";

        }

        for (BaseConfigure config : columnAxisSet) {

            String dimCode = config.getCode();
            Dimension dim = this.findDimension(dimCode, tuple.getDimensionSet());
            if (null == dim || !config.getHasSubtotal()) {
                continue;
            }

            String leftOrTopColumn = this.getAxisDimColumn(columnAxisSet, alias, tuple, orgDimCodeSet, dimCode);
            group += ", (" + leftOrTopColumn + ")";

            String otherAxis = this.getAxisDimColumn(rowAxisSet, alias, tuple, orgDimCodeSet, null);
            String coluns = leftOrTopColumn + "," + otherAxis;
            group += ", (" + coluns + ")";

        }
        */

        String groupingSets = "";
        if (isColSum && isRowSum) {
            // MySQL 不支持 GROUPING SETS，用 WITH ROLLUP 替代（多维度时会多出中间小计行）
            groupingSets = allColumn + " WITH ROLLUP";
        } else {
            // GROUPING SETS 只有一个集合，等价于普通 GROUP BY
            groupingSets = allColumn;
        }

        return groupingSets;

    }

    /**
     * 查找筛选项
     * @param treeFilter
     * @param filterList
     * @return
     */
    private Filter findFilter(Filter treeFilter, List<Filter> filterList, BuildSqlTuple tuple) {

        Filter resultFilter = null;
        String treeKey = treeFilter.getFilterKey();

        Set<Measure> measureSet = tuple.getMeasureSet();
        Measure measure = getMeasure(treeFilter.getCode(), measureSet);

        if (null != measure) {
            for (Filter filter : filterList) {
                String filterKey = filter.getFilterKey();
                if (treeKey.equalsIgnoreCase(filterKey)) {
                    resultFilter = filter;
                    resultFilter.setColumnId(filter.getColumn());
                    break;
                }
            }
        } else {

            Set<Dimension> dimensionSet = tuple.getDimensionSet();
            Dimension dim = getDimension(treeFilter.getCode(), dimensionSet);
            String idStr = "id";

            for (Filter filter : filterList) {
                String filterKey = filter.getFilterKey();
                if (treeKey.equalsIgnoreCase(filterKey)) {

                    String columnAlias = getColumnAlias(dim);
                    resultFilter = filter;
                    if (DimType.DEGENERATE_DIM.equals(dim.getDimType())) {
                        resultFilter.setColumnId(columnAlias);
                    } else {
                        resultFilter.setColumnId(columnAlias + idStr);
                    }

                    resultFilter.setColumn(columnAlias);

                    break;
                }
            }

        }

        return resultFilter;

    }

    public String buildFilterTree(FilterTree parentTree, Set<FilterTree> filterTreeSet, String whereSql, BuildSqlTuple tuple, String alias, boolean isMeasureDetail) {

        Set<Measure> measureSet = tuple.getMeasureSet();
        List<Filter> filterList = tuple.getQueryParam().getTreeFilterList();

        boolean isFirst = StringUtil.isEmpty(whereSql);
        for (FilterTree filterTree : filterTreeSet) {

            isFirst = StringUtil.isEmpty(whereSql);

            //筛选类型
            FilterType filterType = filterTree.getFilterType();
            if (FilterType.FILTER.equals(filterType)) {

                Filter treeFilter = filterTree.getFilter();

                /**
                 * 换成filter
                 */
                Filter filter = this.findFilter(treeFilter, filterList, tuple);

//                SqlLogicalType sqlLogicalType = filterTree.getSqlLogicalType();
                Measure measure = this.getMeasure(filter.getCode(), measureSet);

                if (null != measure) {
                    whereSql += this.buildMeasWhere(filter, alias, measure, isFirst);
                } else {
                    filter.setAlias(alias);
                    whereSql += this.buildFilterSql(filter, tuple, isFirst, isMeasureDetail);
                }

                isFirst = false;

            } else if (FilterType.CHILDREN.equals(filterType)) {
                if (null != parentTree) {

                    String childWhere = "";

                    SqlLogicalType sqlLogicalType = filterTree.getSqlLogicalType();
                    boolean isChildFirst = StringUtil.isEmpty(whereSql);
                    String logicalSql = this.getLogicType(sqlLogicalType, isChildFirst);

                    String temp = this.buildFilterTree(filterTree, filterTree.getFilterTreeSet(), childWhere, tuple, alias, isMeasureDetail);

                    whereSql += logicalSql + "(" + temp + ")";

                } else {
                    whereSql += " and (" + this.buildFilterTree(filterTree, filterTree.getFilterTreeSet(), whereSql, tuple, alias, isMeasureDetail) + ")";
                }

            }

        }

        return whereSql;

    }

    private String buildMeasWhere(BuildSqlTuple tuple, String alias) {
        return buildMeasWhere(tuple, alias, false);
    }

    private String buildMeasWhere(BuildSqlTuple tuple, String alias, boolean isMeasureDetail) {

        String whereSql = "";
        QueryParam queryParam = tuple.getQueryParam();
        boolean isFilterTree = tuple.isFilterTree();

        if (isFilterTree) {
            List<FilterTree> filterTreeList = queryParam.getFilterTreeList();
            whereSql = this.buildFilterTree(null, new HashSet(filterTreeList), whereSql, tuple, alias, isMeasureDetail);
        } else {

            List<Filter> filterList = queryParam.getFilterList();
            Set<Measure> measureSet = new LinkedHashSet<>();

            if (null != tuple.getMeasureSet()) {
                measureSet.addAll(tuple.getMeasureSet());
            }

            for (Filter filter : filterList) {

                Measure measure = this.getMeasure(filter.getCode(), measureSet);
                if (null != measure) {
                    String logcical = "";
                    if (StringUtil.isNotEmpty(whereSql)) {
                        logcical = " and ";
                    }

                    whereSql += (logcical + this.buildMeasWhere(filter, alias, measure, true));
                }

            }
        }

        /**
         * 所有指标同环比的过滤筛选
         */
        Set<Measure> choiceMeasureSet = tuple.getChoiceMeasureSet();
        for (Measure measure : choiceMeasureSet) {
            List<Ratio> ratioList = measure.getRatioList();
            if (!CollectionUtils.isEmpty(ratioList)) {
                for (Ratio ratio : ratioList) {
                    //同环比
                    //排序类型
                    whereSql += this.buildRatioMeasWhere(ratio, alias, measure);

                }
            }
        }

        return whereSql;

    }

    /**
     * 排序方法
     * @param tuple
     * @param alias
     * @return
     */
    private String buildDetailOrderBy(BuildSqlTuple tuple, String alias) {

        String orderBy = "";
        QueryParam queryParam = tuple.getQueryParam();
        List<Order> orderList = queryParam.getDetailOrderList();

        for (Order order : orderList) {

            SortType sortType = order.getSortType();
            String code = order.getCode();
            List<String> valueList = order.getValueList();
            String orderType = "asc";

            if (SortType.DESC.equals(order.getSortType())) {
                orderType = "desc";
            }

            //统一以id作为对比条件自定义排序
            if (valueList.size() > 0) {

                int idx = 1;
                orderBy += ", case";
                for (String value : valueList) {
                    orderBy += " when  " + alias + ".#q#" + code + "#q#='" + value + "' then " + (idx++);
                }
                orderBy += " end " + orderType;

            } else {
                orderBy += ", " + alias + ".#q#" + code + "#q# " + orderType;
            }

        }

        orderBy = orderBy.replaceFirst(",", "");

        return orderBy;

    }

    /**
     * 排序方法
     * @param tuple
     * @param alias
     * @return
     */
    private String buildOrderBy(BuildSqlTuple tuple, String alias) {
        //order by 处理
        String orderBy = "";
        QueryParam queryParam = tuple.getQueryParam();
        List<Order> orderList = queryParam.getOrderList();
        Set<Dimension> dimensionSet = tuple.getDimensionSet();
        Set<Measure> measureSet = new LinkedHashSet<>();

        if (null != tuple.getMeasureSet()) {
            measureSet.addAll(tuple.getChoiceMeasureSet());
        }

        for (Order order : orderList) {

            Dimension dimension = getDimension(order.getCode(), dimensionSet);
            if (null != dimension && !dimension.isAll()) {
                orderBy = this.buildDimOrder(orderBy, order, alias, dimension);
            }

            if (!"t_F_G_J".equalsIgnoreCase(alias)) {
                Measure measure = this.getMeasure(order.getCode(), measureSet);
                if (null != measure) {
                    order = measure.getOrder();
                    if (RatioColumnType.IN.equals(measure.getRatioColumnType()) || (null != order && (CollectionUtils.isEmpty(measure.getRatioList()) || measure.getRatioList().size() == 0))) {
                        orderBy = this.buildMeasOrder(orderBy, order, alias, measure);
                    }
                }
            }

        }

        /**
         * 所有指标同环比的排序
         */
        Set<Measure> choiceMeasureSet = tuple.getChoiceMeasureSet();
        for (Measure measure : choiceMeasureSet) {
            List<Ratio> ratioList = measure.getRatioList();
            if (!CollectionUtils.isEmpty(ratioList) && !RatioColumnType.IN.equals(measure.getRatioColumnType())) {
                for (Ratio ratio : ratioList) {
                    //同环比
                    //排序类型
                    orderBy = this.buildMeasRatioOrder(orderBy, ratio, alias, measure);

                }
            }
        }

        orderBy = orderBy.replaceFirst(",", "");

        return orderBy;

    }

    /**
     * 指标同环比排序
     * @param ratio
     * @param alias
     * @param measure
     * @return
     */
    private String buildRatioMeasWhere(Ratio ratio, String alias, Measure measure) {

        String whereSql = "";
        /**
         * 指标同环比筛选项
         */
        List<Operator> operatorList = ratio.getOperatorList();
        if (!CollectionUtils.isEmpty(operatorList)) {

            whereSql = " and (";

            String ratioTypeAlias = "M_O_M";
            RatioType ratioType = ratio.getRatioType();
            if (RatioType.YEARYEMOM.equals(ratioType)) {
                ratioTypeAlias = "Y_O_Y";
            }

            String colMeasName = getColumnAlias(measure) + ratioTypeAlias;

            boolean isFirst = true;
            for (Operator operator : operatorList) {
                whereSql += this.builMeasOperator(operator, isFirst, alias, colMeasName);
                isFirst = false;
            }

            whereSql += ")";

        }

        return whereSql;

    }


    private String builMeasOperator(Operator operator, boolean isFirst, String alias, String colMeasName) {

        String whereSql = "";
        //逻辑运算符
        SqlLogicalType logicalType = operator.getSqlLogicalType();
        String logicalSql = this.getLogicType(logicalType, isFirst);
        //操作运算符
        SqlOprType oprType = operator.getSqlOprType();

        if (SqlOprType.BETEEN.equals(oprType)) {
            whereSql += logicalSql + "(" + alias + "." + colMeasName + ">= " + formatSqlStringLiteral(operator.getBegin());
            whereSql += " and " + alias + "." + colMeasName + "<= " + formatSqlStringLiteral(operator.getEnd()) + ")";
        } else if (SqlOprType.GREATER_THAN.equals(oprType)) {
            String value = this.getSqlOneValue(operator);
            whereSql += logicalSql + alias + "." + colMeasName + " > " + value;
        } else if (SqlOprType.SMALLER_THAN.equals(oprType)) {
            String value = this.getSqlOneValue(operator);
            whereSql += logicalSql + alias + "." + colMeasName + " < " + value;
        } else if (SqlOprType.GREATER_THAN_OR_EQUAL.equals(oprType)) {
            String value = this.getSqlOneValue(operator);
            whereSql += logicalSql + alias + "." + colMeasName + " >= " + value;
        } else if (SqlOprType.SMALLER_THAN_OR_EQUAL.equals(oprType)) {
            String value = this.getSqlOneValue(operator);
            whereSql += logicalSql + alias + "." + colMeasName + " <= " + value;
        } else if (SqlOprType.EQUAL.equals(oprType)) {
            String value = this.getSqlOneValue(operator);
            whereSql += logicalSql + alias + "." + colMeasName + " = " + value;
        } else if (SqlOprType.NOT_EQUAL.equals(oprType)) {
            String value = this.getSqlOneValue(operator);
            whereSql += logicalSql + alias + "." + colMeasName + " != " + value;
        } else {
            System.err.println("error sql operator in measure : " + operator.getSqlOprType().getDesc());
        }

        return whereSql;

    }



    /**
     * 指标排序
     * @param filter
     * @param alias
     * @param measure
     * @return
     */
    private String buildMeasWhere(Filter filter, String alias, Measure measure, boolean isFilterFirst) {

        String whereSql = "";
        List<Operator> operatorList = filter.getOperatorList();
        if (!CollectionUtils.isEmpty(operatorList)) {

            SqlLogicalType sqlLogicalType = filter.getSqlLogicalType();
            String logicalSql = this.getLogicType(sqlLogicalType, isFilterFirst);

            whereSql = logicalSql + " (";
            boolean isFirst = true;
            for (Operator operator : operatorList) {

                whereSql += this.builMeasOperator(operator, isFirst, alias, getColumnAlias(measure));
                isFirst = false;

            }

            whereSql += ")";

        }

        return whereSql;

    }

    private String getLogicType(SqlLogicalType logicType, boolean isFirst) {

        String logicalTypeStr = " and ";
        if (isFirst) {
            logicalTypeStr = "";
        } else if (SqlLogicalType.OR.equals(logicType)) {
            logicalTypeStr = " or ";
        }

        return logicalTypeStr;

    }

    /**
     * 指标同环比排序处理
     * @param orderBy
     * @param ratio
     * @param alias
     * @param measure
     * @return
     */
    private String buildMeasRatioOrder(String orderBy, Ratio ratio, String alias, Measure measure) {

        String orderType = "asc";
        Order order = measure.getOrder();
        if (null != order) {

            SortType sortType = order.getSortType();
            if (SortType.DEFAULT.equals(sortType)) {
                return orderBy;
            }

            if (SortType.DESC.equals(sortType)) {
                orderType = "desc";
            }

            String ratioTypeAlias = this.getRatioAlis(ratio.getRatioType());
            String measName = this.getColumnAlias(measure);
            if ("T_M_O".equalsIgnoreCase(alias)) {
                ratioTypeAlias = "";
            } else if ("t_RV".equalsIgnoreCase(alias)) {
                if (null == measure.getAlias() || "".equalsIgnoreCase(measure.getAlias())) {
                    measure.setAlias(measure.getName());
                }
                measName = "_m_alis_" + measure.getAlias().replaceAll("-", "");
            }
            orderBy += ", " + alias + ".#q#" + measName + ratioTypeAlias + "#q# " + orderType;

        }

        return orderBy;

    }

    private String getRatioAlis(RatioType ratioType) {

        String ratioTypeAlias = "M_O_M";
        if (RatioType.YEARYEMOM.equals(ratioType)) {
            ratioTypeAlias = "Y_O_Y";
        } else if (RatioType.MONTHMOM.equals(ratioType)) {
            ratioTypeAlias = "M_M_M";
        } else if (RatioType.WEEKMOM.equals(ratioType)) {
            ratioTypeAlias = "W_O_W";
        } else if (RatioType.CUSTOMIZE.equals(ratioType)) {
            ratioTypeAlias = "CUSTOMIZE";
        } else if (RatioType.FIEXED.equals(ratioType)) {
            ratioTypeAlias = "FIEXED";
        }

        return ratioTypeAlias;

    }

    /**
     * 指标排序处理
     * @param orderBy
     * @param order
     * @param alias
     * @param measure
     * @return
     */
    public static String buildMeasOrder(String orderBy, Order order, String alias, Measure measure) {

        List<String> valueList =  order.getValueList();
        String orderType = "asc";

        if (SortType.DESC.equals(order.getSortType())) {
            orderType = "desc";
        }

        orderBy += ", " + alias + ".#q#" + getColumnAlias(measure) + "#q# " + orderType;

        return orderBy;

    }

    public static Measure getMeasure(String code, Set<Measure> measureSet) {
        Measure meas = null;
        for (Measure measure : measureSet) {
            if (measure.getCode().equals(code)) {
                meas = measure;
                break;
            }
        }
        return meas;
    }

    public final static String buildDimOrder(String orderBy, Order order, String alias, Dimension dimension) {

        List<String> valueList =  order.getValueList();
        String orderType = "asc";

        if (SortType.DESC.equals(order.getSortType())) {
            orderType = "desc";
        }

        String idStr = "id";
        if (DimType.DEGENERATE_DIM.equals(dimension.getDimType())) {
            idStr = "";
        }
        //统一以id作为对比条件自定义排序
        if (valueList.size() > 0) {

            int idx = 1;
            orderBy += ", case";
            for (String value : valueList) {
                value = formatSqlValue(value);
                orderBy += " when " + alias + ".#q#" + getColumnAlias(dimension) + idStr + "#q#='" + value + "' then " + (idx++);
            }
            orderBy += " when true then 9999 end " + orderType;

        } else {

            ViewType viewType = dimension.getViewType();
            if (ViewType.NUMBER.equals(viewType)) {
                orderBy += ", cast(" + alias + ".#q#" + getColumnAlias(dimension) + idStr + "#q# as DECIMAL(22, 2))" + orderType;
            } else {
                orderBy += ", " + alias + ".#q#" + getColumnAlias(dimension) + idStr + "#q# " + orderType;
            }

        }

        return orderBy;

    }

    public static Dimension getDimension(String code, Set<Dimension> dimensionSet) {
        Dimension dim = null;
        for (Dimension dimension : dimensionSet) {
            if (dimension.getCode().equals(code)) {
                dim = dimension;
                break;
            }
        }
        return dim;
    }

    private String getColumnDimension(Set<Dimension> dimensionSet, Map<String, List<SingleFactTableSqlAgg>> rootTableMap, String alias, Set<String> orgDimCodeSet) {
        String column = "";
        for (Dimension dimension : dimensionSet) {

            DimType dimType = dimension.getDimType();

            if (!orgDimCodeSet.contains(dimension.getCode()) || dimension.isAll()) {
                continue;
            }
            //标准维度
            if (DimType.STD_WITHOUT_TABLE.equals(dimType) || DimType.STD_WITH_TABLE.equals(dimType)) {
                column += ", " + alias + ".#q#" + getColumnAlias(dimension) + "id#q#";
            } else if (DimType.DEGENERATE_DIM.equals(dimType) || DimType.CUSTOM.equals(dimType)) {
                //退化维
                column += ", " + alias + ".#q#" + getColumnAlias(dimension) + "#q#";
            }
        }

        column = column.replaceFirst(", ", "");
        return  column;

    }

    @Override
    public String buildFullJoinGroupSql(String tmpTable, BuildSqlTuple tuple) {
        return null;
    }

    @Override
    public String buildAggregatorSql(BuildSqlTuple tuple) {

        String alias = "t_AGG";
        String sql = "select ";
        String column = this.getColumnDimension(tuple.getDimensionSet(), tuple.getRootTableMap(), alias, tuple.getDisplayDimensionCodeSet());
        //获取所有Measure
        String columnMeasure = this.getColumnMeasure(tuple, tuple.getRootTableMap(), alias);
        String from = tuple.getFullJoinGroupSql();

        String allColumnMeasure = "";
        boolean isNullColumn = tuple.isNullColumn();
        if (!isNullColumn) {
            allColumnMeasure = " " + column + "," + columnMeasure;
        } else {
            allColumnMeasure = columnMeasure;
        }

        tuple.setAggregatorSql(sql + allColumnMeasure + " from (" + from + ") as " + alias);

        //内存拼接用的sql
        String tempFrom = tuple.getFullJoinTempGroupSql();
        tuple.setAggregatorTempSql(sql + allColumnMeasure + " from (" + tempFrom + ") as " + alias);

        return tuple.getAggregatorSql();
    }

    private String buildAggBaseMeasure(Measure measure, String alias, StringBuffer exCodeBuffer) {

        String exCode = exCodeBuffer.toString();
        String columnMeasure = "";

        if (StringUtil.isEmpty(exCode)) {
            columnMeasure += alias + ".#q#" + this.getExpMeasure(measure, alias) + "#q#";
        } else {
            columnMeasure += alias + ".#q#ex" + this.getExpMeasure(measure, alias) + exCode + "#q#";
        }

        return columnMeasure;

    }

    private String buildAggColumnMeasure(Measure measure, String alias, StringBuffer exCodeBuffer) {

        String columnMeasure = "";

        MeasureType measureType = measure.getMeasType();
        if (MeasureType.DERIVED.equals(measureType) || MeasureType.EXTENDED.equals(measureType)) {
            exCodeBuffer.append("_");
            exCodeBuffer.append(measure.getCode());
        }

        List<Table> measureFactTables = measure.getFactTable();
        String exCode = exCodeBuffer.toString();

        if (MeasureType.ORIGIN.equals(measureType)) {
            columnMeasure += this.buildAggBaseMeasure(measure, alias, new StringBuffer(exCodeBuffer));
        } else {

            String exp = this.getExpression(measure, new StringBuffer(exCodeBuffer), alias);
//            measure.setExpression(exp);
            columnMeasure += exp + " as #q#" + getColumnAlias(measure) + "#q#";

        }

        return columnMeasure;

    }
    /**
     * 生成衍生 select 指标表达式
     * @param tuple
     * @param rootTableMap
     * @param alias
     * @return
     */
    public String getColumnMeasure(BuildSqlTuple tuple, Map<String, List<SingleFactTableSqlAgg>> rootTableMap, String alias) {

        String columnMeasure = "";
        Set<Measure> measureSet = tuple.getUseMeasureSet();
//        Set<Measure> measureSet = tuple.getChoiceMeasureSet();
        for (Measure measure : measureSet) {

            if (!this.isUseMeasure(tuple, measure, measureSet)) {
                continue;
            }

            StringBuffer exCodeBuffer = new StringBuffer();

            MeasureType measureType = measure.getMeasType();
            String current = this.buildAggColumnMeasure(measure, alias, exCodeBuffer);
            columnMeasure += ", " + current;

        }

        columnMeasure = columnMeasure.replaceFirst(",", "");
        return columnMeasure;

    }

    private String getExpression(Measure orgMeasure, StringBuffer exCodeBuffer, String alias) {

        String exCode = exCodeBuffer.toString();
        String exp = orgMeasure.getExpression();
        String regex = "\\{(.*?)}";
        Pattern pattern = Pattern.compile(regex);
        Matcher matcher = pattern.matcher(exp);
        Table useFactTable = orgMeasure.getUseTempFactTable();

        while (matcher.find()) {

            String measCode = matcher.group(1);
            Measure measure = this.findMeasure(measCode, useFactTable.getHasAllMeasureSet());

            if (MeasureType.ORIGIN.equals(measure.getMeasType())) {

                StringBuffer currentScopeBuffer = new StringBuffer(exCodeBuffer);
                String aliasColumn = this.buildAggColumnMeasure(measure, alias, new StringBuffer(currentScopeBuffer));
                String sonExp = "COALESCE(" + aliasColumn + ", 0)*1.000";

                exp = exp.replaceAll("\\{" + measure.getCode() + "\\}", sonExp);

            } else if (MeasureType.EXTENDED.equals(measure.getMeasType()) || MeasureType.DERIVED.equals(measure.getMeasType())) {
                StringBuffer currentScopeBuffer = new StringBuffer(exCodeBuffer);
                currentScopeBuffer.append("_");
                currentScopeBuffer.append(measure.getCode());
                String sonExp = "(" + this.getExpression(measure, new StringBuffer(currentScopeBuffer), alias) + ")";
                exp = exp.replaceAll("\\{" + measure.getCode() + "\\}", sonExp);
            }

        }

        return exp;

    }

    private String repairDivideByZero(String exp) {

        //fixme
        //除数为0
        Integer index = exp.indexOf("/");
        if (index > 0) {
            exp = QueryExecutorConfig.EXP_DIVIDE_BY_ZERO_BEGIN + exp + QueryExecutorConfig.EXP_DIVIDE_BY_ZERO_END;
        }

        return exp;

    }

    /**
     * @param measure
     * @param alias
     * @return
     */
    private String getExpMeasure(Measure measure, String alias) {

        String exp = null;
        if (MeasureType.ORIGIN.equals(measure.getMeasType())) {
            //原生
//            return measure.getName();
            exp = getColumnAlias(measure);
        }

        return exp;

    }

    @Override
    public String buildHasMeasDetailOprSql(BuildSqlTuple tuple) {

        /**
         * 排序
         */
        String orderBy = this.buildDetailOrderBy(tuple, "T_M_O");
        String orderBySql = "";
        if (StringUtil.isNotEmpty(orderBy)) {
            orderBySql = "order by " + orderBy;
        }

        /**
         * 指标筛选
         */
        String where = this.buildMeasWhere(tuple, "T_M_O", true);
        if (StringUtil.isNotEmpty(where)) {
            where = " where 1=1 " + where + " ";
        }
//        String hasMeasOrderSql =  "select *, row_number() over (" + orderBySql + ") as rank from (" + tuple.getAggregatorSql() + ") as T_M_O " + where + orderBySql;
        String hasMeasOrderSql =  "select * from (" + tuple.getAggregatorSql() + ") as T_M_O " + where + orderBySql;

        return hasMeasOrderSql;

    }

    @Override
    public String buildHasMeasOprSql(BuildSqlTuple tuple) {

        /**
         * 指标排序
         */
        String orderBy = this.buildOrderBy(tuple, "T_M_O");
        String orderBySql = "";
        if (StringUtil.isNotEmpty(orderBy)) {
            orderBySql = "order by " + orderBy;
        }

        /**
         * 指标筛选
         */
        String where = this.buildMeasWhere(tuple, "T_M_O");
        if (StringUtil.isNotEmpty(where)) {

            where = where.trim();
            if (!where.startsWith("and")) {
                where = "and " + where;
            }

            where = " where 1=1 " + where + " ";
        }
//        String hasMeasOrderSql =  "select *, row_number() over (" + orderBySql + ") as rank from (" + tuple.getAggregatorSql() + ") as T_M_O " + where + orderBySql;
        String hasMeasOrderSql =  "select * from (" + tuple.getAggregatorSql() + ") as T_M_O " + where + orderBySql;

        return hasMeasOrderSql;

    }

    @Override
    public String  buildHasMeasOprPageSql(BuildSqlTuple tuple) {
        Integer startPage = tuple.getStartPage();
        Integer endPage = tuple.getEndPage();
        Integer range  = endPage - startPage;

//        String pageSql =  "select * from (" + tuple.getFullJoinGroupSql() + " limit 2000) as T_F_G_J where rank>" + startPage + " and rank<=" + endPage;
//        String pageSql =  "select * from (" + tuple.getFullJoinGroupSql() + ") as T_F_G_J where rank>" + startPage + " and rank<=" + endPage;

//        String pageSql =  "select * from (" + tuple.getFullJoinGroupSql() + ") as T_F_G_J limit " + range + " offset " + startPage;
        String pageSql =  tuple.getFullJoinGroupSql() + " limit " + range + " offset " + startPage;
        tuple.setFullJoinGroupSql(pageSql);

        return pageSql;
    }

    @Override
    public String buildCountSql(String fullJoinGroupSql, BuildSqlTuple tuple) {
        String sql =  "select count(1) as cnt from (" + fullJoinGroupSql + ") as _CNT";
        tuple.setFullJoinGroupSqlWithCount(sql);

        String tempSql =  "select count(1) as cnt from (" + tuple.getFullJoinTempGroupSql() + ") as _CNT";
        tuple.setFullJoinTempGroupSqlWithCount(tempSql);

        return sql;
    }

    @Override
    public String buildPageSql(BuildSqlTuple tuple) {

        Integer startPage = tuple.getStartPage();
        Integer endPage = tuple.getEndPage();
        Integer range = endPage - startPage;
//        String pageSql =  "select * from (" + tuple.getFullJoinGroupSql() + " limit 2000) as t_F_G_J where rank>" + startPage + " and rank<=" + endPage;
//        String pageSql =  "select * from (" + tuple.getFullJoinGroupSql() + ") as t_F_G_J where rank>" + startPage + " and rank<=" + endPage;
//        String pageSql =  "select * from (" + tuple.getFullJoinGroupSql() + " limit " + startPage + ", " + endPage + ") as t_F_G_J";
        String pageSql =  "select * from (" + tuple.getFullJoinGroupSql() + " limit " + range + " offset " + startPage + ") as t_F_G_J";
        tuple.setFullJoinGroupSql(pageSql);
        tuple.setFullJoinTempGroupSql(tuple.getFullJoinTempGroupSql() + " limit " + range + " offset " + startPage);

        return pageSql;

    }

    @Override
    public String buildReViewSQL(BuildSqlTuple tuple) {

        String sql = this.buildBaseReViewSQL(tuple.getAggregatorSql(), tuple, false);
        tuple.setReviewSql(sql);

        return tuple.getReviewSql();
    }

    private String buildBaseReViewSQL(String aggregatorSql, BuildSqlTuple tuple, boolean isPlan) {

        String alias = "t_RV";
        String from = "(" + aggregatorSql + ") " + alias;
        String column = "";
        String join = "";

        Set<Dimension> dimensionSet = tuple.getChoiceDimensionSet();
        for (Dimension dimension : dimensionSet) {

            if (!tuple.getDisplayDimensionCodeSet().contains(dimension.getCode()) || dimension.isAll()) {
                continue;
            }

            boolean isStandard = DimType.STD_WITH_TABLE.equals(dimension.getDimType());
            boolean isGroupColumn = !CollectionUtils.isEmpty(dimension.getGroupColumnList());

            String dimName = getColumnAlias(dimension);
            boolean hasSubTotal = tuple.hasSubtotal();

            if (isStandard) {

                //维度表有多个时，默认取第一个。
                Integer defIdx = 0;
                Table dimTable = dimension.getDimTableList().get(defIdx);

                String dimCode = dimension.getCode();
                String dimTableName = this.getTableName(dimTable);
                String onlyTableName = dimTable.getTableName() + "_" + dimCode;

                String displayDimPrimaryKeyId = dimTable.getDimPrimaryKey();
                String displayDimName = dimTable.getDimColumn();

                /*
                 *  根据维度是否与事实表直接有应用关系，判断是否需要进行回现标题的处理。此处在事实表上有外键
                 *  的需要进行回现处理，通过杂项维进行分组的已经在rootSql中回现完成，无需处理，只正常提取ID，Name既可。
                 */
                //基本维度
                String whereDt = "";
                String displayName = "displayName_" + dimCode;
                if (isPlan) {

                    if (dimTable.getHasColumnDT()) {
                        String tempDimTableName = "\"TEMP\".\"" +  dimTable.getTableName();
                        String lastDaySql = "SELECT MAX(dt) FROM " + tempDimTableName;
                        whereDt = " where dt=(" + lastDaySql + ")";
                    }

                    column += ", " + alias + ".#q#" + dimName + "id#q# as #q#" + dimName + "ID#q#, " + onlyTableName + "." + displayName + " as #q#" + dimName + "#q#";
                    String onlyOneDim = "(select " + displayDimPrimaryKeyId + ", " + displayDimName + " as " + displayName + " from \"TEMP\".\"" +  dimTable.getTableName() + "\"" + whereDt + " group by " + displayDimPrimaryKeyId + ", " + displayDimName + ")";
                    join += " left join " + onlyOneDim + " as " + onlyTableName + " on " + alias + ".#q#" + dimName + "ID#q#=" + onlyTableName + "." + displayDimPrimaryKeyId;;

                } else {

                    ViewType viewType = dimension.getViewType();
                    boolean isSelfOption = displayDimPrimaryKeyId.equalsIgnoreCase(displayDimName);

//                    if (dimension.isDegDim() || isSelfOption) {
//                  先不考虑优化，默认从
                    if (false) {

                        //月份需要特殊处理
                        if (ViewType.MONTH.equals(viewType)) {
                            column += ", " + alias + ".#q#" + dimName + "id#q# as #q#" + dimName + "ID#q#, date_format(" + alias + ".#q#" + dimName + "id#q#, '%Y-%m') as #q#" + dimName + "#q#";
                        } else {
                            /**
                             * 维度表主键、columnId、columnName都等于事实表上的外键列，则省略join交表直接从事实表上出数据。
                             */
                            column += ", " + alias + ".#q#" + dimName + "id#q# as #q#" + dimName + "ID#q#, " + alias + ".#q#" + dimName + "id#q# as #q#" + dimName + "#q#";
                        }

                    } else {

                        /**
                         * 必须通过交表解决回现名称
                         */
                        if (dimTable.getHasColumnDT()) {
                            String lastDaySql = "SELECT MAX(dt) FROM " + dimTableName;
                            whereDt = " where dt=(" + lastDaySql + ")";
                        }

                        String colId = alias + ".#q#" + dimName + "id#q#";

                        // 若使用了 dimColumnExpr（CASE 表达式），则 ID 本身已是可读的分类名，无需外层 JOIN
                        if (dimTable.getDimColumnExpr() != null && !dimTable.getDimColumnExpr().isEmpty()) {
                            if (hasSubTotal) {
                                column += ", " + colId + " as #q#" + dimName + "ID#q#, coalesce(" + colId + ", '" + IndicatorConstant.ALL_SUBTATAL + "') as #q#" + dimName + "#q#";
                            } else {
                                column += ", " + colId + " as #q#" + dimName + "ID#q#, " + colId + " as #q#" + dimName + "#q#";
                            }
                        } else {
                            if (hasSubTotal) {
                                column += ", " + colId + " as #q#" + dimName + "ID#q#, coalesce(" + onlyTableName + "." + displayName + ", coalesce(" + colId + ", '" + IndicatorConstant.ALL_SUBTATAL + "')) as #q#" + dimName + "#q#";
                            } else {
                                column += ", " + colId + " as #q#" + dimName + "ID#q#, coalesce(" + onlyTableName + "." + displayName + ", " + colId + ") as #q#" + dimName + "#q#";
                            }

                            String onlyOneDim = "(select " + displayDimPrimaryKeyId + ", " + displayDimName + " as " + displayName + " from " + dimTableName + whereDt + " group by " + displayDimPrimaryKeyId + ", " + displayDimName + ")";
                            join += " left join " + onlyOneDim + " as " + onlyTableName + " on " + alias + ".#q#" + dimName + "ID#q#=" + onlyTableName + "." + displayDimPrimaryKeyId;
                        }

                    }

                }

                if (IndicatorConstant.STANDARD_DIMENSION_NO_TABLE.equalsIgnoreCase(dimTableName)) {
                    join += " and " + dimTableName + ".dimCode='" + dimension.getCode() + "'";
                }

            } else if (DimType.STD_WITHOUT_TABLE.equals(dimension.getDimType())) {
                Table dimTable = dimension.getDimTableList().get(0);
                //无维表
                if (hasSubTotal) {
                    column += ", " + alias + ".#q#" + dimName + "id#q# as #q#" + dimName + "ID#q#, coalesce(swt" + dimension.getCode() + ".#q#v_value#q#, '" + IndicatorConstant.ALL_SUBTATAL + "')  as #q#" + dimName + "#q#";
                } else {
                    column += ", " + alias + ".#q#" + dimName + "id#q# as #q#" + dimName + "ID#q#, swt" + dimension.getCode() + ".#q#v_value#q# as #q#" + dimName + "#q#";
                }
                String onlyOneDim = dimTable.getSchemaName() + "." + dimTable.getTableName();
                join += " left join " + onlyOneDim + " as swt" + dimension.getCode() + " on " + alias + ".#q#" + dimName + "ID#q#=swt" + dimension.getCode() + ".v_key and swt" + dimension.getCode() + ".code='" + dimension.getCode() + "'";

            } else {
                //退化维
                if (hasSubTotal) {
                    column += ", coalesce(" + alias + ".#q#" + dimName + "#q#, '" + IndicatorConstant.ALL_SUBTATAL + "') as #q#" + dimName + "#q#";
                } else {
                    column += ", " + alias + ".#q#" + dimName + "#q# as #q#" + dimName + "#q#";
                }

            }

        }

        Set<Measure> allMeasureSet = tuple.getChoiceMeasureSet();
        Set<Measure> authMeasureSet = tuple.getAuthMeasureSet();

        Set<String> colMeasSet = new HashSet<String>();
        for (Measure measure : allMeasureSet) {

            String measureName = getColumnAlias(measure);
            String measAlias = getMeasureAlias(measure);

            String measColumn = alias + ".#q#" + measureName + "#q#";

            if (!authMeasureSet.contains(measure)) {
                column += ", '*' as #q#" + measAlias + "#q#";
                continue;
            }

            //指标同环比类型
            RatioType ratioType = measure.getRatioType();
            String sonColumn = "";

            List<Ratio> ratioList = measure.getRatioList();
            if ((!CollectionUtils.isEmpty(ratioList) && ratioList.size() > 1) || ratioList.size() == 0 || RatioColumnType.IN.equals(measure.getRatioColumnType())) {

                sonColumn = this.format(measColumn, measure) + " as #q#" + measAlias + "#q#";
                column += ", " + sonColumn;

                if (!CollectionUtils.isEmpty(ratioList)) {
                    for (Ratio ratio : ratioList) {

                        ratioType = ratio.getRatioType();
                        if (RatioType.MONTHONMONTH.equals(ratioType)) {
                            column = this.buildRatioColumn(alias, measureName, measAlias, measure, column, "M_O_M");
                        } else if (RatioType.YEARYEMOM.equals(ratioType)) {
                            column = this.buildRatioColumn(alias, measureName, measAlias, measure, column, "Y_O_Y");
                        } else if (RatioType.FIEXED.equals(ratioType)) {
                            column = this.buildRatioColumn(alias, measureName, measAlias, measure, column, "FIEXED");
                        }
                    }
                }

            } else {

                //结果为值还是率
                RatioValueType ratioValueType = RatioValueType.RATIO;
                if (RatioValueType.VALUE.equals(measure.getRatioValueType())) {
                    ratioValueType = measure.getRatioValueType();
                } else {
                    //率值名称取别名
                    measureName = measAlias;
                }

                FormatType formatType = FormatType.PERCENT2;
                Boolean isDiff = this.isHasDiff(measure);
                if (isDiff) {
                    formatType = FormatType.THOUSANDTH;
                }

                ValueFormat valueFormat = null;
                if (null != measure.getValueFormat()) {
                    valueFormat = measure.getValueFormat();
                } else {
                    valueFormat = new ValueFormat();
                    valueFormat.setFormatType(formatType);
                }

                if (RatioType.MONTHONMONTH.equals(ratioType)) {

                    if (RatioValueType.VALUE.equals(ratioValueType)) {

                        measColumn = alias + ".#q#" + measureName + "ORG_M_O_M#q#";
                        sonColumn = this.format(measColumn, measure) + " as #q#" + measAlias + "#q#";
                        column += ", " + sonColumn;

                    } else {

//                        String valueColumn = alias + ".#q#" + measureName + "ORG_M_O_M" + this.buildRatioAlias(measure) + "#q#";

                        measColumn = alias + ".#q#" + measureName + "M_O_M#q#";
                        sonColumn = this.format(measColumn, valueFormat) + " as #q#" + measAlias + "#q#";
                        column += ", " + sonColumn;

                    }

                } else if (RatioType.WEEKMOM.equals(ratioType)) {

                    if (RatioValueType.VALUE.equals(ratioValueType)) {

                        measColumn = alias + ".#q#" + measureName + "ORG_W_O_W#q#";
                        sonColumn = this.format(measColumn, measure) + " as #q#" + measAlias + "#q#";
                        column += ", " + sonColumn;

                    } else {

                        measColumn = alias + ".#q#" + measureName + "W_O_W#q#";
                        sonColumn = this.format(measColumn, valueFormat) + " as #q#" + measAlias + "#q#";
                        column += ", " + sonColumn;

                    }

                } else if (RatioType.MONTHMOM.equals(ratioType)) {

                    if (RatioValueType.VALUE.equals(ratioValueType)) {

                        measColumn = alias + ".#q#" + measureName + "ORG_M_M_M#q#";
                        sonColumn = this.format(measColumn, measure) + " as #q#" + measAlias + "#q#";
                        column += ", " + sonColumn;

                    } else {

                        measColumn = alias + ".#q#" + measureName + "M_M_M#q#";
                        sonColumn = this.format(measColumn, valueFormat) + " as #q#" + measAlias + "#q#";
                        column += ", " + sonColumn;

                    }

                } else if (RatioType.YEARYEMOM.equals(ratioType)) {

                    if (RatioValueType.VALUE.equals(ratioValueType)) {

                        measColumn = alias + ".#q#" + measureName + "ORG_Y_O_Y#q#";
                        sonColumn = this.format(measColumn, measure) + " as #q#" + measAlias + "#q#";
                        column += ", " + sonColumn;

                    } else {

                        measColumn = alias + ".#q#" + measureName + "Y_O_Y#q#";
                        sonColumn = this.format(measColumn, valueFormat) + " as #q#" + measAlias + "#q#";
                        column += ", " + sonColumn;

                    }

                } else if (RatioType.FIEXED.equals(ratioType)) {
                    column = this.buildRatioColumn(ratioValueType, measColumn, alias, measureName, measure, measAlias, column, "FIEXED", formatType);
                } else if (RatioType.CUSTOMIZE.equals(ratioType)) {
                    column = this.buildRatioColumn(ratioValueType, measColumn, alias, measureName, measure, measAlias, column, "CUSTOMIZE", formatType);
                }

            }

        }

        String orderBy = this.buildOrderBy(tuple, alias);

        //用到所有维度
        column = column.replaceFirst(",", "");
        String sql = "select " + column + " from " + from + " " + join;

        if (orderBy.length() > 0) {
            sql += " order by " + orderBy;
        }

        return sql;

    }

    private boolean isHasDiff(Measure measure) {
        boolean hasDiff = false;
        List<Ratio> ratioList = measure.getRatioList();
        if (!CollectionUtils.isEmpty(ratioList)) {
            for (Ratio ratio : ratioList) {
                if (RatioExpType.DIFF.equals(ratio.getRatioExpType())) {
                    hasDiff = true;
                    break;
                }
            }
        }

        return hasDiff;

    }

    private String buildRatioColumn(String alias, String measureName, String measAlias, Measure measure, String column, String ratioName) {

        String measColumn = alias + ".#q#" + measureName + "ORG_" + ratioName + this.buildRatioAlias(measure) + "#q#";
        String sonColumn = this.format(measColumn, measure) + " as #q#" + measAlias + "ORG_" + ratioName + "#q#";
        column += ", " + sonColumn;

        ValueFormat valueFormat = new ValueFormat();
        valueFormat.setFormatType(FormatType.PERCENT2);
        measColumn = alias + ".#q#" + measureName + ratioName + "#q#";
        sonColumn = this.format(measColumn, valueFormat) + " as #q#" + measAlias + ratioName + "#q#";
        column += ", " + sonColumn;

        return column;

    }

    private String buildRatioColumn(RatioValueType ratioValueType, String measColumn, String alias, String measureName, Measure measure, String measAlias, String column, String ratioAlias, FormatType formatType) {

        if (RatioValueType.VALUE.equals(ratioValueType)) {

            measColumn = alias + ".#q#" + measureName + ratioAlias + "#q#";
            String sonColumn = this.format(measColumn, measure) + " as #q#" + measAlias + "#q#";
            column += ", " + sonColumn;

        } else {

            ValueFormat valueFormat = new ValueFormat();
            valueFormat.setFormatType(formatType);
            measColumn = alias + ".#q#" + measureName + ratioAlias + "#q#";
            String sonColumn = this.format(measColumn, valueFormat) + " as #q#" + measAlias + "#q#";
            column += ", " + sonColumn;

        }

        return column;
    }

    private String format(String column, ValueFormat valueFormat) {

        String formatColumn = null;
        if (null == valueFormat) {
            //容错，如果未设置，默认取null
            valueFormat = new ValueFormat();
            valueFormat.setFormatType(FormatType.DECIMAL);
            valueFormat.setValue(4);

//            valueFormat.setFormatType(FormatType.THOUSANDTH);
        }

        FormatType formatType = valueFormat.getFormatType();

        if (FormatType.DECIMAL.equals(formatType)) {
            //自定义小数
            Integer value = valueFormat.getValue();
            formatColumn = "round(" + column + ", " + value + ")";

        } else if (FormatType.DECIMAL1.equals(formatType)) {
            formatColumn = "round(" + column + ", 1)";
        } else if (FormatType.DECIMAL2.equals(formatType)) {
            formatColumn = "round(" + column + ", 2)";
        } else if (FormatType.INTEGER.equals(formatType)) {
            formatColumn = "round(" + column + ", 0)";
        } else if (FormatType.PERCENT.equals(formatType)) {
            //自定义小数
            Integer value = valueFormat.getValue();
            formatColumn = "concat(round(" + column + "*100, " + value + "), '%')";

        } else if (FormatType.PERCENT1.equals(formatType)) {
            formatColumn = "concat(round(" + column + "*100, 1), '%')";
        } else if (FormatType.PERCENT2.equals(formatType)) {
            formatColumn = "concat(round(" + column + "*100, 2), '%')";
        } else if (FormatType.THOUSANDTH.equals(formatType)) {
            // Use CAST(ROUND(..., 0) AS CHAR) instead of FORMAT() for Doris/StarRocks compatibility
            // FORMAT(decimal, int) is MySQL-only and not supported by Doris/StarRocks
            String rounded = "CAST(ROUND(CAST(" + column + " AS DOUBLE), 0) AS CHAR)";
            formatColumn = "case when " + column + " >= 1 or " + column + " <= -1 then " + rounded + " when " + column + " = 0 then 0 else cast(round(" + column + ", 4) as char) end";
        } else if (FormatType.MILLION.equals(formatType)) {
//            formatColumn = "case when " + column + " >= 1 or " + column + " <= -1 then case starts_with(regexp_replace(money_format(" + column + "/1000000), '\\\\.00', ''), '.') when 1 then concat('0', regexp_replace(money_format(" + column + "/1000000), '\\\\.00', '')) else regexp_replace(money_format(" + column + "/1000000), '\\\\.00', '') end when " + column + " = 0 then 0 else 0 end";
            //百万需要进行格式化，此处不能够进行格式化。只能在最终回显的地方处理。
            formatColumn = column;
        } else {
            formatColumn = column;
        }

        log.info(formatColumn);

        return formatColumn;

    }

    private String format(String column, Measure measure) {

        String formatColumn = null;
        ValueFormat valueFormat = measure.getValueFormat();

        if (null == valueFormat) {
            //容错，如果未设置，默认取null
            valueFormat = new ValueFormat();
        }

        return this.format(column, valueFormat);

    }

    private String getTableName(Table table) {
        return QueryExecutorConfig.QUERY_ENGINE_PREFIX + table.getSchemaName() + "." + table.getTableName();
    }

    @Override
    public String buildAggregationSQL(String aggregatorSql, BuildSqlTuple tuple) {
        return null;
    }

    /**
     * 获取维度集合下的所有维度表。
     * @param tuple
     * @return
     */
    @Override
    public List<TableSchemaInfo> getDimTableInfo(BuildSqlTuple tuple) {

        Set<Dimension> dimensionSet = tuple.getDimensionSet();
        List<TableSchemaInfo> schemaInfoList = new LinkedList<>();

        for (Dimension dimension : dimensionSet) {

            boolean isStandard = DimType.STD_WITH_TABLE.equals(dimension.getDimType());

            if (null != dimension.getDimTableList()) {

                for (Table table : dimension.getDimTableList()) {

                    if (isStandard && StringUtil.isNotEmpty(table.getTableName())) {

                        TableSchemaInfo tableSchemaInfo = new TableSchemaInfo();
                        tableSchemaInfo.setSource(String.valueOf(table.getSourceType()));
                        tableSchemaInfo.setSchema(table.getSchemaName());
                        tableSchemaInfo.setTable(table.getTableName());

                        schemaInfoList.add(tableSchemaInfo);

                    }

                }

            }

        }

        tuple.setDimTableSchemaInfoList(schemaInfoList);

        return schemaInfoList;

    }

    public static String setAlias(String sql, String alias) {
        return setAlias(sql, alias, "");
    }

    public static String getColumnName(String column, String alias) {

        DbType dbType = JdbcConstants.MYSQL;
        String sql = "select " + column + " from dual";

        List<SQLStatement> statementList = SQLUtils.parseStatements(sql, dbType);
        String whereSql = "";
        for (SQLStatement statement : statementList) {
            List<SQLObject> sqlObjectList = statement.getChildren();
            for (SQLObject sqlObject : sqlObjectList) {
                SQLSelect sqlSelect = (SQLSelect) sqlObject;
                MySqlSelectQueryBlock sqlSelectQuery = (MySqlSelectQueryBlock)sqlSelect.getQuery();
                SQLSelectItem sqlExpr = (SQLSelectItem)sqlSelectQuery.getSelectItem(0);
                SQLExpr sqlDept = sqlExpr.getExpr();
                if (sqlDept instanceof SQLMethodInvokeExpr) {

                    List<SQLObject> childrenList = sqlDept.getChildren();
                    if (!CollectionUtils.isEmpty(childrenList)) {

                        for (SQLObject object : childrenList) {
                            if (object instanceof SQLIdentifierExpr) {
                                SQLIdentifierExpr sqlIndentExpr = (SQLIdentifierExpr) object;
                                sqlIndentExpr.setName(alias + "." + sqlIndentExpr.getName());
                            }
                        }

                    }

                    column = sqlDept.toString();

                }

            }

        }

        return column;

    }

    public static String setAlias(String sql, String alias, String where) {

        if (StringUtil.isEmpty(sql)) {
            return "";
        }

        DbType dbType = JdbcConstants.MYSQL;

        sql = "select * from dual where 1 = 1 AND " + sql + where;

        List<SQLStatement> statementList = SQLUtils.parseStatements(sql, dbType);
        String whereSql = "";
        for (SQLStatement statement : statementList) {
            List<SQLObject> sqlObjectList = statement.getChildren();
            for (SQLObject sqlObject : sqlObjectList) {
                SQLSelect sqlSelect = (SQLSelect) sqlObject;
                MySqlSelectQueryBlock sqlSelectQuery = (MySqlSelectQueryBlock)sqlSelect.getQuery();
                SQLBinaryOpExpr sqlExpr = (SQLBinaryOpExpr)sqlSelectQuery.getWhere();
                SQLExpr sqlDept = sqlExpr.getRight();
                analysis(sqlExpr, alias);
                whereSql = sqlExpr.toString().replaceAll("\n\t", " ").replaceAll("\t", "");

            }

        }

        whereSql = whereSql.replaceFirst("1 = 1 AND ", "");

        return whereSql;
    }

    private static void analysis(SQLBinaryOpExpr sqlExper, String alias) {

        SQLExpr rightSqlExpr = sqlExper.getRight();

        if (rightSqlExpr instanceof SQLBinaryOpExpr) {
            analysis((SQLBinaryOpExpr)rightSqlExpr, alias);
        } else {
            visitSqlMethod(rightSqlExpr, alias);
        }

        SQLExpr leftSqlExpr = sqlExper.getLeft();
        if (leftSqlExpr instanceof SQLBinaryOpExpr) {
            analysis((SQLBinaryOpExpr)leftSqlExpr, alias);
        } else {
            visitSqlMethod(leftSqlExpr, alias);
        }
    }

    private static void visitSqlMethod(SQLExpr sqlExpr, String alias) {

        if (sqlExpr instanceof SQLAggregateExpr) {

            SQLAggregateExpr expr = (SQLAggregateExpr)sqlExpr;
            SQLExpr from = expr.getFrom();
            visitSqlMethod(from, alias);

            SQLExpr filter = expr.getFilter();
            visitSqlMethod(filter, alias);

            SQLExpr owner = expr.getOwner();
            visitSqlMethod(owner, alias);

            SQLExpr exprFor = expr.getFor();
            visitSqlMethod(exprFor, alias);

            SQLExpr exprUsing = expr.getUsing();
            visitSqlMethod(exprUsing, alias);

            List<SQLExpr> arguments = expr.getArguments();
            for (SQLExpr argument : arguments) {

                if (argument instanceof SQLIdentifierExpr) {
                    SQLIdentifierExpr aexpr = (SQLIdentifierExpr)argument;
                    aexpr.setName(alias + "." + aexpr.getName());
                }

            }

            SQLOver over = expr.getOver();
            if (null != over) {

                List<SQLExpr> partitionList = over.getPartitionBy();
                for (SQLExpr sqlExpr1 : partitionList) {
                    visitSqlMethod(sqlExpr1, alias);
                }

                SQLOrderBy sqlOrderBy = over.getOrderBy();
                List<SQLSelectOrderByItem> itemList = sqlOrderBy.getItems();
                for (SQLSelectOrderByItem sqlSelectOrderByItem : itemList) {
                    SQLExpr sqlExpr1 = sqlSelectOrderByItem.getExpr();
                    visitSqlMethod(sqlExpr1, alias);
                }

            }

        } else if (sqlExpr instanceof SQLMethodInvokeExpr) {
            SQLMethodInvokeExpr method = (SQLMethodInvokeExpr)sqlExpr;
            List<SQLExpr> arguments = method.getArguments();
            for (SQLExpr argument : arguments) {

                if (argument instanceof SQLIdentifierExpr) {
                    SQLIdentifierExpr expr = (SQLIdentifierExpr)argument;
                    expr.setName(alias + "." + expr.getName());
                } else if (argument instanceof SQLMethodInvokeExpr) {
                    visitSqlMethod(argument, alias);
                }

            }
        } else if (sqlExpr instanceof SQLIntegerExpr) {
            SQLIntegerExpr expr = (SQLIntegerExpr)sqlExpr;
        } else if (sqlExpr instanceof SQLCharExpr) {
            SQLCharExpr expr = (SQLCharExpr)sqlExpr;
        } else if (sqlExpr instanceof SQLIdentifierExpr) {
            SQLIdentifierExpr expr = (SQLIdentifierExpr)sqlExpr;
            expr.setName(alias + "." + expr.getName());
        } else if (sqlExpr instanceof SQLInListExpr) {

            SQLInListExpr expr = (SQLInListExpr)sqlExpr;
            SQLExpr sonExpr = expr.getExpr();

            visitSqlMethod(sonExpr, alias);

        } else if (sqlExpr instanceof SQLBetweenExpr) {

            SQLBetweenExpr expr = (SQLBetweenExpr)sqlExpr;
            SQLExpr sonExpr = expr.getTestExpr();

            visitSqlMethod(sonExpr, alias);

        } else if (sqlExpr instanceof SQLArrayExpr) {

            SQLArrayExpr expr = (SQLArrayExpr)sqlExpr;
            SQLExpr sonExpr = expr.getExpr();

            visitSqlMethod(sonExpr, alias);

        } else if (sqlExpr instanceof SQLBinaryOpExprGroup) {

            SQLBinaryOpExprGroup expr = (SQLBinaryOpExprGroup)sqlExpr;
            List<SQLExpr> sonExprList = expr.getItems();

            for (SQLExpr sqlExpr1 : sonExprList) {
                visitSqlMethod(sqlExpr1, alias);
            }

        } else if (sqlExpr instanceof SQLCaseExpr) {

            SQLCaseExpr expr = (SQLCaseExpr)sqlExpr;
            SQLExpr elseExpr = expr.getElseExpr();
            visitSqlMethod(elseExpr, alias);

            SQLExpr valueExpr = expr.getValueExpr();
            visitSqlMethod(valueExpr, alias);

        } else if (sqlExpr instanceof SQLCaseStatement) {

            SQLCaseStatement expr = (SQLCaseStatement)sqlExpr;
            SQLExpr valueExpr1 = expr.getValueExpr();
            visitSqlMethod(valueExpr1, alias);

        } else if (sqlExpr instanceof SQLCastExpr) {

            SQLCastExpr expr = (SQLCastExpr)sqlExpr;
            SQLExpr sonExpr = expr.getExpr();
            visitSqlMethod(sonExpr, alias);

        } else if (sqlExpr instanceof SQLContainsExpr) {

            SQLContainsExpr expr = (SQLContainsExpr)sqlExpr;
            SQLExpr sonExpr = expr.getExpr();
            visitSqlMethod(sonExpr, alias);

        } else if (sqlExpr instanceof SQLDateTimeExpr) {

            SQLDateTimeExpr expr = (SQLDateTimeExpr)sqlExpr;
            SQLExpr literal = expr.getLiteral();
            visitSqlMethod(literal, alias);

        } else if (sqlExpr instanceof SQLDbLinkExpr) {

            SQLDbLinkExpr expr = (SQLDbLinkExpr)sqlExpr;
            SQLExpr sonExpr = expr.getExpr();
            visitSqlMethod(sonExpr, alias);

        } else if (sqlExpr instanceof SQLExtractExpr) {

            SQLExtractExpr expr = (SQLExtractExpr)sqlExpr;
            SQLExpr value = expr.getValue();
            visitSqlMethod(value, alias);

        } else if (sqlExpr instanceof SQLFlashbackExpr) {

            SQLFlashbackExpr expr = (SQLFlashbackExpr)sqlExpr;
            SQLExpr value = expr.getExpr();
            visitSqlMethod(value, alias);

        } else if (sqlExpr instanceof SQLInSubQueryExpr) {

            SQLInSubQueryExpr expr = (SQLInSubQueryExpr)sqlExpr;
            SQLExpr value = expr.getExpr();
            visitSqlMethod(value, alias);

        } else if (sqlExpr instanceof SQLListExpr) {

            SQLListExpr expr = (SQLListExpr)sqlExpr;
            List<SQLExpr> itemList = expr.getItems();
            for (SQLExpr sqlExpr1 : itemList) {
                visitSqlMethod(sqlExpr1, alias);
            }

        } else if (sqlExpr instanceof SQLMatchAgainstExpr) {

            SQLMatchAgainstExpr expr = (SQLMatchAgainstExpr)sqlExpr;
            SQLExpr value = expr.getAgainst();
            visitSqlMethod(value, alias);

        } else if (sqlExpr instanceof SQLMethodInvokeExpr) {

            SQLMethodInvokeExpr expr = (SQLMethodInvokeExpr)sqlExpr;
            List<SQLExpr> itemList = expr.getArguments();
            for (SQLExpr sqlExpr1 : itemList) {
                visitSqlMethod(sqlExpr1, alias);
            }

            SQLExpr from = expr.getFrom();
            visitSqlMethod(from, alias);

            SQLExpr owner = expr.getOwner();
            visitSqlMethod(owner, alias);

            SQLExpr exprFor = expr.getFor();
            visitSqlMethod(exprFor, alias);

            SQLExpr exprUsing = expr.getUsing();
            visitSqlMethod(exprUsing, alias);

        } else if (sqlExpr instanceof SQLNotExpr) {

            SQLNotExpr expr = (SQLNotExpr)sqlExpr;
            SQLExpr value = expr.getExpr();
            visitSqlMethod(value, alias);

        } else if (sqlExpr instanceof SQLPropertyExpr) {

            SQLPropertyExpr expr = (SQLPropertyExpr)sqlExpr;
            SQLExpr value = expr.getOwner();
            visitSqlMethod(value, alias);

        } else if (sqlExpr instanceof SQLSizeExpr) {

            SQLSizeExpr expr = (SQLSizeExpr)sqlExpr;
            SQLExpr value = expr.getValue();
            visitSqlMethod(value, alias);

        } else if (sqlExpr instanceof SQLTimeExpr) {

            SQLTimeExpr expr = (SQLTimeExpr)sqlExpr;
            SQLExpr value = expr.getLiteral();
            visitSqlMethod(value, alias);

        } else if (sqlExpr instanceof SQLUnaryExpr) {

            SQLUnaryExpr expr = (SQLUnaryExpr)sqlExpr;
            SQLExpr value = expr.getExpr();
            visitSqlMethod(value, alias);

        } else if (sqlExpr instanceof SQLValuesExpr) {

            SQLValuesExpr expr = (SQLValuesExpr)sqlExpr;
            List<SQLListExpr> lists = expr.getValues();
            for (SQLListExpr expr1 : lists) {
                visitSqlMethod(expr1, alias);
            }


        }

        else {
            System.err.println(sqlExpr);
        }

    }

    @Override
    public boolean checkOpenRadioInfo(BuildSqlTuple tuple) {
        Set<Measure> choiceMeasureSet = tuple.getChoiceMeasureSet();
        for (Measure measure : choiceMeasureSet) {
            List<Ratio> ratioList = measure.getRatioList();
            if (!CollectionUtils.isEmpty(ratioList)) {
                for (Ratio ratio : ratioList) {

                    RatioType ratioType = ratio.getRatioType();
                    if (!RatioType.DEFAULT.equals(ratioType)) {
                        return true;
                    }

                }
            }

        }
        return false;
    }

    /**
     * 获取同环比的维度
     * @param tuple
     * @return
     */
    public static Dimension getDimByRadio(BuildSqlTuple tuple) {

        Dimension radioDim = null;
        Integer sequence = Integer.valueOf(0);

        Set<Dimension> dimensionSet = tuple.getDimensionSet();
        Set<String> displayDimensionCodeSet = tuple.getDisplayDimensionCodeSet();
        for (Dimension dimension : dimensionSet) {

            ViewType viewType = dimension.getViewType();
            Level level = dimension.getLevel();
            boolean hasDisplay = displayDimensionCodeSet.contains(dimension.getCode());
            if (hasDisplay && isDateViewType(viewType) && (null == sequence || (null != level && level.getSequence() >= sequence))) {
                radioDim = dimension;
            } else if (null == level && isDateViewType(viewType)) {
                radioDim = dimension;
            }

        }

        tuple.setRadioDim(radioDim);

        return radioDim;

    }

    /**
     * 获取指标中出现的同环比类型,最大同时拥有环比、同比
     * @param tuple
     * @return
     */
    private List<RatioType> getRatioTypeList(BuildSqlTuple tuple) {

        List<RatioType> ratioTypeList = new LinkedList<>();

        Set<Measure> choiceMeasureSet = tuple.getChoiceMeasureSet();
        for (Measure measure : choiceMeasureSet) {
            List<Ratio> ratioList = measure.getRatioList();
            if (!CollectionUtils.isEmpty(ratioList)) {
                for (Ratio ratio : ratioList) {

                    RatioType ratioType = ratio.getRatioType();
                    if (!RatioType.DEFAULT.equals(ratioType)
                            && !RatioType.FIEXED.equals(ratioType)
                                && !RatioType.CUSTOMIZE.equals(ratioType)
                                    && !ratioTypeList.contains(ratioType)) {
                        ratioTypeList.add(ratioType);
                    }

                }
            }
        }

        return ratioTypeList;

    }

    /**
     * 根据同环比维度构建root sql，此处需要处理日期条件，把条件放到筛选时间到上个节点
     * @param ratio
     * @param ratioType
     * @param tuple
     * @param radioDim
     * @return
     */
    private String buildOnlyRadioTypeSql(Ratio ratio, RatioType ratioType, BuildSqlTuple tuple, Dimension radioDim) {

        //设置同环比维度
        tuple.setRadioDim(radioDim);
        //设置同环比比率
        tuple.setRatioType(ratioType);
        
        if (null != ratio && RatioType.FIEXED.equals(ratio.getRatioType())) {

            //移除上个SqlCreater的filter.
            String SqlCreater = "SqlCreater";
            Filter ratioFilter = null;
            List<Filter> removeFilterList = new ArrayList<>();
            for (Filter filter : tuple.getQueryParam().getFilterList()) {
                if (SqlCreater.equalsIgnoreCase(filter.getCreator()) || radioDim.getCode().equalsIgnoreCase(filter.getCode())) {
                    removeFilterList.add(filter);
                }
            }

            if (removeFilterList.size() > 0) {
                tuple.getQueryParam().getFilterList().removeAll(removeFilterList);
            }

            //新增加当前的sqlFilter.
            ratioFilter = new Filter();
            ratioFilter.setCreator(SqlCreater);
            ratioFilter.setCode(radioDim.getCode());
            List<Operator> operatorList = new LinkedList<>();
            Operator operator = new Operator();
            operator.setTimeRange(TimeRange.DATE);
            Integer viewType = radioDim.getViewType().getValue();

            String currentDate = ratio.getRatioValue();

            List<String> dataList = Arrays.asList(currentDate);
            operator.setDataList(dataList);
            operator.setSqlOprType(SqlOprType.IN);
            operator.setBegin(currentDate);
            operator.setEnd(currentDate);

            operatorList.add(operator);
            ratioFilter.setViewType(radioDim.getViewType());
            ratioFilter.setOperatorList(operatorList);

            ratioFilter.setHierarchyId(Long.valueOf(1));

            chartQueryService.initDayTimeRange(operator, ratioFilter, false);

            tuple.setRadioDim(null);
            tuple.setFixedFilter(true);
            tuple.getQueryParam().getFilterList().add(ratioFilter);

        }

        Map<String, List<SingleFactTableSqlAgg>> rootTableMap = this.getBySourceTable(tuple);
        tuple.setRootTableMap(rootTableMap);

        List<String> rootSqls = this.buildRootSqls(tuple);
        String fullJoinGroupSql = this.buildFullJoinGroupSql(tuple);

        String aggregatorSql = this.buildAggregatorSql(tuple);

        //同环比维度设置为null
        tuple.setFixedFilter(false);
        tuple.setRadioDim(null);
        tuple.setRatioType(null);

//        String viewSql = QueryExecutorService.formatSql(SourceType.DORIS, aggregatorSql);
//        System.out.println(viewSql);

        return aggregatorSql;

    }

    private String buildDimId(Dimension dimension) {
        DimType dimType = dimension.getDimType();
        String id = "id";
        if (DimType.DEGENERATE_DIM.equals(dimType)) {
            id = "";
        }
        return id;
    }

    @Override
    public String buildRadioSql(BuildSqlTuple tuple, String orgSql) {

        //1、确定连接日期维度
        Dimension radioDim = this.getDimByRadio(tuple);

        String ratioDimId = this.getColumnAlias(radioDim) + this.buildDimId(radioDim);
        ViewType viewType = radioDim.getViewType();
        //2、获取连接出现的指标同环比类型
        List<RatioType> ratioTypeList = this.getRatioTypeList(tuple);

        //3、构建连接表，连接表日期筛选的维度需要至少小于基础维度一个基本时间单元.
        String ratioFrom = "from (" + orgSql + ") AS O_R_G";
        String select = "select O_R_G.* ";

        //定制
        Set<Measure> choiceMeasureSet = tuple.getChoiceMeasureSet();
        for (Measure measure : choiceMeasureSet) {
            List<Ratio> ratioList = measure.getRatioList();
            if (!CollectionUtils.isEmpty(ratioList)) {
                for (Ratio ratio : ratioList) {

                    String ratioDimCode = ratio.getDimCode();
                    if (StringUtil.isNotEmpty(ratioDimCode)) {
                        radioDim = tuple.findDimension(ratioDimCode);
                        viewType = radioDim.getViewType();
                        ratioDimId = this.getColumnAlias(radioDim) + this.buildDimId(radioDim);
                    }

                    String ratioOnDimId = ratioDimId;

                    RatioType ratioType = ratio.getRatioType();
                    if (RatioType.FIEXED.equals(ratioType) || RatioType.CUSTOMIZE.equals(ratioType)) {

                        String ratioTypeSql = this.buildOnlyRadioTypeSql(ratio, ratioType, tuple, radioDim);

                        String alias = null;
                        if (RatioType.FIEXED.equals(ratioType)) {
                            alias = "FIEXED";
                        } else if (RatioType.CUSTOMIZE.equals(ratioType)) {
                            alias = "CUSTOMIZE";
                        }

                        alias += this.buildRatioAlias(measure);

                        String onlyTypeSql = "(" + ratioTypeSql + ") AS " + alias;

                        //构建同环比join条件
                        String ratioWhere = "";
                        if (RatioType.CUSTOMIZE.equals(ratioType)) {
                            ratioWhere = this.buildRatioOnWhere(ratio, radioDim, ratioType, alias);
                        }

                        String otherDimWhere = this.buildRatioOnDimWhere("O_R_G", alias, tuple, radioDim);

                        //构建同环比关联条件
                        if (RatioType.FIEXED.equals(ratioType)) {

                            otherDimWhere = otherDimWhere.replaceFirst(" and ", " ");
                            if (StringUtils.isNotBlank(otherDimWhere)) {
                                ratioFrom += " cross join " + onlyTypeSql + " on " + otherDimWhere;
                            } else {
                                ratioFrom += " cross join " + onlyTypeSql;
                            }

                        } else {
                            if (ViewType.SEASON.equals(viewType)) {
                                //季度需要格式化为yyyyq格式
                                ratioFrom += " left join " + onlyTypeSql + " on replace(O_R_G." + ratioDimId + ", '-Q', '')=" + ratioWhere + otherDimWhere;
                            } else {

                                String leftColumn = "O_R_G." + ratioDimId;
                                if (ViewType.WEEK.equals(viewType)) {
                                    leftColumn = "concat(date_format(date_add(str_to_date(concat(REPLACE (" + leftColumn + ", '-W', '' ), ' 1'), '%Y%u %w'), INTERVAL 0 WEEK), '%Y%u'), '')";
                                }

                                ratioFrom += " left join " + onlyTypeSql + " on " + leftColumn + "=" + ratioWhere + otherDimWhere;
                            }

                        }

                        select += this.buildRadioSelect(ratioType, tuple, alias);

                    } else {

                        String ratioTypeSql = this.buildOnlyRadioTypeSql(null, ratioType, tuple, radioDim);

                        String alias = null;
                        if (RatioType.MONTHONMONTH.equals(ratioType)) {
                            alias = "M_O_M";
                        } else if (RatioType.WEEKMOM.equals(ratioType)) {
                            alias = "W_O_W";
                        } else if (RatioType.MONTHMOM.equals(ratioType)) {
                            alias = "M_M_M";
                        } else if (RatioType.YEARYEMOM.equals(ratioType)) {
                            alias = "Y_O_Y";
                        }

                        alias += this.buildRatioAlias(measure);

                        String onlyTypeSql = "(" + ratioTypeSql + ") AS " + alias;

                        //构建同环比join条件
                        String ratioWhere = this.buildRatioOnWhere(null, radioDim, ratioType, alias);
                        String otherDimWhere = this.buildRatioOnDimWhere("O_R_G", alias, tuple, radioDim);

                        //构建同环比关联条件

                        if (ViewType.SEASON.equals(viewType)) {
                            //季度需要格式化为yyyyq格式
                            ratioFrom += " left join " + onlyTypeSql + " on replace(O_R_G." + ratioDimId + ", '-Q', '')=" + ratioWhere + otherDimWhere;
                        } else {
                            String leftColumn = "O_R_G." + ratioDimId;
                            if (ViewType.WEEK.equals(viewType)) {
                                leftColumn = "concat(date_format(date_add(str_to_date(concat(REPLACE (" + leftColumn + ", '-W', '' ), ' 1'), '%Y%u %w'), INTERVAL 0 WEEK), '%Y%u'), '')";
                            }
                            ratioFrom += " left join " + onlyTypeSql + " on " + leftColumn + "=" + ratioWhere + otherDimWhere;
                        }

                        select += this.buildRadioSelect(ratioType, tuple, alias);

                    }
                }
            }
        }

//        ratioFrom += " order by O_R_G." + ratioDimId + " ";
        //4、构建关联指标，并计算同环比。
        select += this.buildRadioExp(tuple);
        //5、最终构建sql
        String radioSql = select + ratioFrom;
        tuple.setAggregatorSql(radioSql);

        return radioSql;

    }

    private String buildRatioAlias(Measure measure) {

        String measAlias = "";
        if (null != measure.getAlias()) {
            measAlias = measure.getAlias().replaceAll("-", "");
        }

        String alias = "_" + measure.getCode() + "_" + measAlias;

        return alias;

    }

    /**
     * 构建同环比数学公式
     * @param tuple
     * @return
     */
    private String buildRadioExp(BuildSqlTuple tuple) {

        String column = "";
        Set<Measure> choiceMeasureSet = tuple.getChoiceMeasureSet();
        for (Measure measure : choiceMeasureSet) {

            String ratioAlias = buildRatioAlias(measure);
            String columnName = getColumnAlias(measure);
            String measAlias = getMeasureAlias(measure);
            List<Ratio> ratioList = measure.getRatioList();
            for (Ratio ratio : ratioList) {

                RatioType ratioType = ratio.getRatioType();
                RatioExpType ratioExpType = ratio.getRatioExpType();
                String orgMeas = "O_R_G.#q#" + columnName + "#q#";
                String momMeas = "M_O_M" + ratioAlias + ".#q#" + columnName + "#q#";
                String wowMeas = "W_O_W" + ratioAlias + ".#q#" + columnName + "#q#";
                String mmmMeas = "M_M_M" + ratioAlias + ".#q#" + columnName + "#q#";
                String yoyMeas = "Y_O_Y" + ratioAlias + ".#q#" + columnName + "#q#";
                String fiexed = "FIEXED" + ratioAlias + ".#q#" + columnName + "#q#";
                String customize = "CUSTOMIZE" + ratioAlias + ".#q#" + columnName + "#q#";

                String ratioExp;
                if (RatioType.MONTHONMONTH.equals(ratioType)) {
                    ratioExp = this.buildRatioExp(ratioExpType, orgMeas, momMeas);
                    column += ", " + ratioExp + " as #q#" + measAlias + "M_O_M#q#";
                } else if (RatioType.WEEKMOM.equals(ratioType)) {
                    ratioExp = this.buildRatioExp(ratioExpType, orgMeas, wowMeas);
                    column += ", " + ratioExp + " as #q#" + measAlias + "W_O_W#q#";
                } else if (RatioType.MONTHMOM.equals(ratioType)) {
                    ratioExp = this.buildRatioExp(ratioExpType, orgMeas, mmmMeas);
                    column += ", " + ratioExp + " as #q#" + measAlias + "M_M_M#q#";
                } else if (RatioType.YEARYEMOM.equals(ratioType)) {
                    ratioExp = this.buildRatioExp(ratioExpType, orgMeas, yoyMeas);
                    column += ", " + ratioExp + " as #q#" + measAlias + "Y_O_Y#q#";
                } else if (RatioType.FIEXED.equals(ratioType)) {
                    ratioExp = this.buildRatioExp(ratioExpType, orgMeas, fiexed);
                    column += ", " + ratioExp + " as #q#" + measAlias + "FIEXED#q#";
                } else if (RatioType.CUSTOMIZE.equals(ratioType)) {
                    ratioExp = this.buildRatioExp(ratioExpType, orgMeas, customize);
                    column += ", " + ratioExp + " as #q#" + measAlias + "CUSTOMIZE#q#";
                }

            }

        }

        return column;

    }

    /**
     * 构建百分比计算表达式
     * @param ratioExpType
     * @param org 报告期
     * @param yoy 基期
     * @return
     */
    private String buildRatioExp(RatioExpType ratioExpType, String org, String mom) {

        String ratioExp = "";
        if (RatioExpType.DIFFPERCENTAGE.equals(ratioExpType) || null == ratioExpType) {
            ratioExp = "((" + org + "-" + mom + ")/" + mom + ")";
        } else if (RatioExpType.DIFF.equals(ratioExpType)) {
            ratioExp = "(" + org + "-" + mom + ")";
        } else if (RatioExpType.PERCENTAGE.equals(ratioExpType)) {
            ratioExp = "(" + org + "/" + mom + ")";
        }

        return ratioExp;

    }

    /**
     * 构建查询项目
     * @param ratioType
     * @param tuple
     * @param alias
     * @return
     */
    private String buildRadioSelect(RatioType ratioType, BuildSqlTuple tuple, String alias) {

        String column = "";
        Set<Measure> choiceMeasureSet = tuple.getChoiceMeasureSet();
        for (Measure measure : choiceMeasureSet) {

            //如果选中的维度含有指标类型则生成查询项。
            if (this.hasRatioType(measure, ratioType)) {
                String columnName = getColumnAlias(measure);
                column += ", " + alias + ".#q#" + columnName + "#q# as #q#" + columnName + "ORG_" + alias + "#q#";
            }


        }

        return column;

    }

    /**
     * 判断该指标是否含有此维度类型
     * @param measure
     * @param ratioType
     * @return
     */
    private boolean hasRatioType(Measure measure, RatioType ratioType) {

        if (null == ratioType) {
            return false;
        }

        RatioType measRatioType = measure.getRatioType();
        if (ratioType.equals(measRatioType)) {
            return true;
        }

        List<Ratio> ratioList = measure.getRatioList();
        if (!CollectionUtils.isEmpty(ratioList)) {
            for (Ratio ratio : ratioList) {
                if (ratioType.equals(ratio.getRatioType())) {
                    return true;
                }
            }
        }

        return false;

    }

    /**
     * 同环比关联项中的维度关联
     * @param orgAlias
     * @param ratioAlias
     * @param tuple
     * @return
     */
    private String buildRatioOnDimWhere(String orgAlias, String ratioAlias, BuildSqlTuple tuple, Dimension ratioDim) {

        String where = "";
        //页面所选择的维度
        Set<Dimension> choiceDimensionSet = tuple.getChoiceDimensionSet();
        if (!CollectionUtils.isEmpty(choiceDimensionSet)) {
            for (Dimension dimension : choiceDimensionSet) {

                ViewType viewType = dimension.getViewType();
                boolean isDate = this.isDateViewType(viewType);
                if (ratioDim.getCode().equalsIgnoreCase(dimension.getCode())) {
                    continue;
                }

                DimType dimType = dimension.getDimType();
                String id = "id";
                if (DimType.DEGENERATE_DIM.equals(dimType)) {
                    id = "";
                }
                String dimId = this.getColumnAlias(dimension) + id;

                if (ViewType.SEASON.equals(viewType)) {
                    //季度需要格式化为yyyyq格式
                    where += " and replace(" + orgAlias + "." + dimId + ", '-Q', '')=replace(" + ratioAlias + "." + dimId + ", '-Q', '')";
                } else {
                    where += " and " + orgAlias + "." + dimId + "=" + ratioAlias + "." + dimId;
                }

                where += " and " + orgAlias + "." + dimId + "=" + ratioAlias + "." + dimId;

            }
        }

        return where;

    }

    /**
     * 根据维度、同环比类型、别名创建同环比关联条件
     * @param ratio
     * @param radioDim
     * @param ratioType
     * @param alias
     * @return
     */
    private String buildRatioOnWhere(Ratio ratio, Dimension radioDim, RatioType ratioType, String alias) {
        //列名
        String ratioWhere = null;
        String id = "id";
        DimType dimType = radioDim.getDimType();
        if (DimType.DEGENERATE_DIM.equals(dimType)) {
            id = "";
        }

        String ratioDimId = this.getColumnAlias(radioDim) + id;
        String column = alias + "." + ratioDimId;
        ViewType viewType = radioDim.getViewType();

        if (ViewType.DAY.equals(viewType)) {
            if (RatioType.MONTHONMONTH.equals(ratioType)) {
                ratioWhere = "concat(date_format(date_add(" + column + ", INTERVAL 1 DAY), '%Y-%m-%d'), '')";
            } else if (RatioType.WEEKMOM.equals(ratioType)) {
                ratioWhere = "concat(date_format(date_add(str_to_date(concat(" + column + ",''), '%Y-%m-%d'), INTERVAL 1 week), '%Y-%m-%d'), '')";
            } else if (RatioType.MONTHMOM.equals(ratioType)) {
                ratioWhere = "concat(date_format(date_add(str_to_date(concat(" + column + ",''), '%Y-%m-%d'), INTERVAL 1 MONTH), '%Y-%m-%d'), '')";
            } else if (RatioType.YEARYEMOM.equals(ratioType)) {
                ratioWhere = "concat(date_format(date_add(" + column + ", INTERVAL 1 YEAR), '%Y-%m-%d'), '')";
            } else if (RatioType.CUSTOMIZE.equals(ratioType)) {
                RatioOperaType ratioOperaType = ratio.getRatioOperaType();
                String value = ratio.getRatioValue();
                if (RatioOperaType.BEFORE.equals(ratioOperaType)) {
                    ratioWhere = "concat(date_format(date_add(" + column + ", INTERVAL " + value + " DAY), '%Y-%m-%d'), '')";
                } else {
                    ratioWhere = "concat(date_format(date_sub(" + column + ", INTERVAL " + value + " DAY), '%Y-%m-%d'), '')";
                }
            }
        } else if (ViewType.WEEK.equals(viewType)) {
            if (RatioType.MONTHONMONTH.equals(ratioType)) {
                ratioWhere = "concat(date_format(date_add(str_to_date(concat(replace(" + column + ", '-W', ''),' 1'), '%Y%u %w'), INTERVAL 1 week), '%Y%u'), '')";
            } else if (RatioType.YEARYEMOM.equals(ratioType)) {
                ratioWhere = "concat(date_format(date_add(str_to_date(concat(replace(" + column + ", '-W', ''),' 1'), '%Y%u %w'), INTERVAL 1 YEAR), '%Y%u'), '')";
            } else if (RatioType.CUSTOMIZE.equals(ratioType)) {
                RatioOperaType ratioOperaType = ratio.getRatioOperaType();
                String value = ratio.getRatioValue();
                if (RatioOperaType.BEFORE.equals(ratioOperaType)) {
                    ratioWhere = "concat(date_format(date_add(str_to_date(concat(replace(" + column + ", '-W', ''),' 1'), '%Y%u %w'), INTERVAL " + value + " week), '%Y%u'), '')";
                } else {
                    ratioWhere = "concat(date_format(date_sub(str_to_date(concat(replace(" + column + ", '-W', ''),' 1'), '%Y%u %w'), INTERVAL " + value + " week), '%Y%u'), '')";
                }
            }
        } else if (ViewType.MONTH.equals(viewType)) {
            if (RatioType.MONTHONMONTH.equals(ratioType)) {
                ratioWhere = "concat(date_format(date_add(str_to_date(concat(" + column + ",'-01'), '%Y-%m-%d'), INTERVAL 1 MONTH), '%Y-%m'), '')";
            } else if (RatioType.YEARYEMOM.equals(ratioType)) {
                ratioWhere = "concat(date_format(date_add(str_to_date(concat(" + column + ",'-01'), '%Y-%m-%d'), INTERVAL 1 YEAR), '%Y-%m'), '')";
            } else if (RatioType.CUSTOMIZE.equals(ratioType)) {
                RatioOperaType ratioOperaType = ratio.getRatioOperaType();
                String value = ratio.getRatioValue();
                if (RatioOperaType.BEFORE.equals(ratioOperaType)) {
                    ratioWhere = "concat(date_format(date_add(str_to_date(concat(" + column + ",'-01'), '%Y-%m-%d'), INTERVAL " + value + " MONTH), '%Y-%m'), '')";
                } else {
                    ratioWhere = "concat(date_format(date_sub(str_to_date(concat(" + column + ",'-01'), '%Y-%m-%d'), INTERVAL " + value + " MONTH), '%Y-%m'), '')";
                }
            }
        } else if (ViewType.SEASON.equals(viewType)) {
            if (RatioType.MONTHONMONTH.equals(ratioType)) {
                ratioWhere = "concat(YEAR(date_add(str_to_date(concat(strleft(" + column + ", 4), regexp_replace(concat('0', strright(" + column + ", 1) * 3 - 2), '010', '10'), '01'), '%Y%m%d'), INTERVAL 3 MONTH)), '', floor(month(date_add(str_to_date(concat(strleft(" + column + ", 4), regexp_replace(concat('0', strright(" + column + ", 1) * 3 - 2), '010', '10'), '01'), '%Y%m%d'), INTERVAL 3 MONTH))/3 + 1))";
            } else if (RatioType.YEARYEMOM.equals(ratioType)) {
                ratioWhere = "concat(YEAR(date_add(str_to_date(concat(strleft(" + column + ", 4), regexp_replace(concat('0', strright(" + column + ", 1) * 3 - 2), '010', '10'), '01'), '%Y%m%d'), INTERVAL 1 YEAR)), '', floor(month(date_add(str_to_date(concat(strleft(" + column + ", 4), regexp_replace(concat('0', strright(" + column + ", 1) * 3 - 2), '010', '10'), '01'), '%Y%m%d'), INTERVAL 1 YEAR))/3 + 1))";
            } else if (RatioType.CUSTOMIZE.equals(ratioType)) {
                RatioOperaType ratioOperaType = ratio.getRatioOperaType();
                String value = ratio.getRatioValue();
                if (RatioOperaType.BEFORE.equals(ratioOperaType)) {
                    ratioWhere = "concat(YEAR(date_add(str_to_date(concat(strleft(" + column + ", 4), regexp_replace(concat('0', strright(" + column + ", 1) * 3 - 2), '010', '10'), '01'), '%Y%m%d'), INTERVAL 3*" + value + " MONTH)), '', floor(month(date_add(str_to_date(concat(strleft(" + column + ", 4), regexp_replace(concat('0', strright(" + column + ", 1) * 3 - 2), '010', '10'), '01'), '%Y%m%d'), INTERVAL 3 MONTH))/3 + 1))";
                } else {
                    ratioWhere = "concat(YEAR(date_sub(str_to_date(concat(strleft(" + column + ", 4), regexp_replace(concat('0', strright(" + column + ", 1) * 3 - 2), '010', '10'), '01'), '%Y%m%d'), INTERVAL 3*" + value + " MONTH)), '', floor(month(date_add(str_to_date(concat(strleft(" + column + ", 4), regexp_replace(concat('0', strright(" + column + ", 1) * 3 - 2), '010', '10'), '01'), '%Y%m%d'), INTERVAL 3 MONTH))/3 + 1))";
                }
            }
        } else if (ViewType.YEAR.equals(viewType)) {

            if (RatioType.CUSTOMIZE.equals(ratioType)) {
                RatioOperaType ratioOperaType = ratio.getRatioOperaType();
                String value = ratio.getRatioValue();
                if (RatioOperaType.BEFORE.equals(ratioOperaType)) {
                    ratioWhere = "concat(" + column + "+" + value + ", '')";
                } else {
                    ratioWhere = "concat(" + column + "-" + value + ", '')";
                }
            } else {
                ratioWhere = "concat(" + column + "+1, '')";
            }
        }

        return ratioWhere;

    }

}
