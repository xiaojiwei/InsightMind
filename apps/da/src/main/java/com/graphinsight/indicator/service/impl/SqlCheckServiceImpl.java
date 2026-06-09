package com.graphinsight.indicator.service.impl;

import com.alibaba.druid.DbType;
import com.alibaba.druid.sql.SQLUtils;
import com.alibaba.druid.sql.ast.SQLExpr;
import com.alibaba.druid.sql.ast.SQLObject;
import com.alibaba.druid.sql.ast.SQLStatement;
import com.alibaba.druid.sql.ast.expr.SQLBinaryOpExpr;
import com.alibaba.druid.sql.ast.expr.SQLIdentifierExpr;
import com.alibaba.druid.sql.ast.expr.SQLMethodInvokeExpr;
import com.alibaba.druid.sql.ast.statement.SQLSelect;
import com.alibaba.druid.sql.ast.statement.SQLSelectItem;
import com.alibaba.druid.sql.dialect.mysql.ast.statement.MySqlSelectQueryBlock;
import com.alibaba.druid.util.JdbcConstants;
import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.TypeReference;
import com.baomidou.dynamic.datasource.annotation.DS;
import com.baomidou.dynamic.datasource.toolkit.DynamicDataSourceContextHolder;
import com.google.common.base.Preconditions;
import com.graphinsight.indicator.enums.DimType;
import com.graphinsight.indicator.enums.JdbcDataSourceType;
import com.graphinsight.indicator.enums.MeasureType;
import com.graphinsight.indicator.model.*;
import com.graphinsight.indicator.model.dto.DimensionHistogramRequest;
import com.graphinsight.indicator.model.dto.FactTable;
import com.graphinsight.indicator.model.dto.HistogramInfo;
import com.graphinsight.indicator.service.IndicatorService;
import com.graphinsight.indicator.service.SqlCheckService;
import com.graphinsight.indicator.util.StringUtil;
import com.graphinsight.indicator.util.TempThreadLocalUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.info.ProjectInfoProperties;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;

import java.math.BigDecimal;
import java.util.*;

@Service
public class SqlCheckServiceImpl implements SqlCheckService {

    @Autowired
    @Qualifier("secondJdbcTemplate")
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private IndicatorService indicatorService;

    @Override
    public void checkMeasureNull(Set<Measure> measureSet) {
         Preconditions.checkArgument(!CollectionUtils.isEmpty(measureSet), "指标不能为空，请联系至少选择一个指标。measures is null");
    }

    @Override
    public void checkMeasureFactTable(Measure measure) {
        List<Table> factTable = measure.getFactTable();
        TempThreadLocalUtil.set("owner", measure.getCreator());
        Preconditions.checkArgument(!CollectionUtils.isEmpty(factTable), this.buildErrorMeasInfo(measure,  "指标没有支持的事实表，请联系指标负责人修复。 factTable is null"));
    }

    @Override
    public void checkMeasureExpression(Measure measure) {
        List<Table> factTableList = measure.getFactTable();
        for (Table table : factTableList) {
            String expression = table.getExpression();
            TempThreadLocalUtil.set("owner", measure.getCreator());
            this.checkStr(expression, this.buildErrorMeasInfo(measure,"指标没有配置聚合表达式，请联系指标负责人修复。Table is " + table.getTableName() + " expression is null"));
        }
    }

    public void checkCollection(Collection value, String errorInfo) {
        Preconditions.checkArgument(!CollectionUtils.isEmpty(value), errorInfo);
    }

    public void checkStr(String value, String errorInfo) {
        Preconditions.checkArgument(!StringUtil.isEmpty(value), errorInfo);
    }

    private String buildInfo(Set<Dimension> dimSet) {
        StringBuilder infoBuilder = new StringBuilder();
        if (!CollectionUtils.isEmpty(dimSet)) {
            for (Dimension dim : dimSet) {

                infoBuilder.append("{")
                        .append(dim.getName())
                        .append(" ")
                        .append(dim.getCode())
                        .append("}");

            }
        }
        return infoBuilder.toString();
    }

    @Override
    public void checkFactTable(Measure meas, Set<Table> tableSet, Set<Dimension> dimSet) {
        String errorInfo = "指标与维度没有可以共用的事实表，请联系指标负责人修复。Measure Name is : [" + meas.getName() + "] Dim ids is : [" + this.buildInfo(dimSet) + "] not have factTable is :";
        for (Table table : tableSet) {
            errorInfo += " : " + table.getTableName();
        }
        TempThreadLocalUtil.set("owner", meas.getCreator());
        Preconditions.checkArgument(!CollectionUtils.isEmpty(meas.getFactTable()), errorInfo);
    }

    @Override
    public void checkObj(Object obj, String errorInfo) {
        Preconditions.checkArgument(null != obj, errorInfo);
    }

    @Override
    public void checkMeasureExprRun(Measure measure) {
        TempThreadLocalUtil.set("owner", measure.getCreator());
        List<Table> factTableList = measure.getFactTable();
        for (Table factTable : factTableList) {
            String exp = factTable.getExpression();
            // Skip null or non-JSON expressions (e.g. formula strings for DERIVED measures)
            if (exp == null || !exp.trim().startsWith("[")) continue;

            List<OperationItem> itemBOList = JSON.parseObject(exp, new TypeReference<ArrayList<OperationItem>>() {});
            for (OperationItem itemBO : itemBOList) {
                if (OperationItem.OPERATOR.equalsIgnoreCase(itemBO.getOperatingType())) {

                    String tableName = factTable.getTableName();
                    //事实表名为空
                    this.checkStr(tableName, this.buildErrorMeasInfo(measure, "指标表达式为空，请联系指标负责人修复。table is null"));
                    String schema = factTable.getSchemaName();
                    //schema信息为空
                    this.checkStr(schema, this.buildErrorMeasInfo(measure, "指标表数据库为空，请联系指标负责人修复。table info :" + tableName + " schema is null"));
                    //事实列信息
                    String factColumn = factTable.getFactColumn();
                    this.checkStr(factColumn, this.buildErrorMeasInfo(measure, "指标度量列为空，请联系指标负责人修复。table info :" + factColumn + " factColumn is null"));
                    String operator = itemBO.getOperator();
                    //聚合函数
                    this.checkStr(factColumn, this.buildErrorMeasInfo(measure, "指标聚合方式为空，请联系指标负责人修复。table info :" + operator + " operator is null"));

                    String checkSql = "select sum(t1.meas) from (select " + BuildSqlServiceImpl.getExp(operator, "T0", factTable) + " as meas from " + schema + "." + tableName + " as T0 limit 0) as t1";

                    try {
//                        List<Map<String, Object>> list = jdbcTemplate.queryForList(checkSql);
                    } catch (Exception ex) {
                        ex.printStackTrace();
                        throw new IllegalArgumentException(this.buildErrorMeasInfo(measure, "指标基本sql验证不通过，请联系指标负责人修复。" + ex.getMessage()));
                    }

                }
            }
        }
    }

    private String buildErrorMeasInfo(Measure measure, String errorInfo) {
        TempThreadLocalUtil.set("owner", measure.getCreator());
        return "measureInfo of code:'" + measure.getCode() + "' name:'" + measure.getName() + "'" + errorInfo;
    }

    private String buildErrorDimInfo(Dimension dimension, String errorInfo) {
        TempThreadLocalUtil.set("owner", dimension.getCreator());
        return "dimensionInfo of code:'" + dimension.getCode() + "' name:'" + dimension.getName() + "'" + errorInfo;
    }

    @Override
    public void checkDimensionNull(Set<Dimension> dimensionSet) {
        Preconditions.checkArgument(!CollectionUtils.isEmpty(dimensionSet), "维度集合为空，请选择合适的维度。dimension is null");
    }

    @Override
    public void checkDimensionFactTable(Dimension dimension) {
        List<Table> factTableList = dimension.getFactTableList();
        TempThreadLocalUtil.set("owner", dimension.getCreator());
        Preconditions.checkArgument(!CollectionUtils.isEmpty(factTableList), this.buildErrorDimInfo(dimension, "维度的事实表为空，请联系维度负责人修复。factTable is null"));
    }

    @Override
    public void checkDimensionDimTable(Dimension dimension) {
        DimType dimType = dimension.getDimType();
        TempThreadLocalUtil.set("owner", dimension.getCreator());
        //标准维有维表验证
        if (DimType.STD_WITH_TABLE.equals(dimType) || DimType.STD_WITH_TABLE.equals(dimType)) {
            List<Table> dimTableList = dimension.getDimTableList();
            Preconditions.checkArgument(!CollectionUtils.isEmpty(dimTableList), this.buildErrorDimInfo(dimension, "维度的维度表为空，请联系维度负责人修复。dimTableList is null"));
        }
    }

    private boolean hasTable(List<Table> measFactTableList, Table factTable) {

        boolean hasTable = false;
        if (!CollectionUtils.isEmpty(measFactTableList)) {
            for (Table table : measFactTableList) {
                if (null != table.getTableName() && table.getTableName().equalsIgnoreCase(factTable.getTableName())) {
                    hasTable = true;
                    break;
                } else if (null == table.getTableName()) {
                    hasTable = true;
                    break;
                }
            }
        }

        return hasTable;
    }

    @Override
    public void checkNoDimTableSql(Dimension dim, List<Table> measFactTableList, boolean isQuery) {

        List<Table> dimTableList = dim.getDimTableList();
        TempThreadLocalUtil.set("owner", dim.getCreator());
        //维度表已经进行过验证，此处必然有维度表数据
        Table dimTable = dimTableList.get(0);
        List<Table> factTableList = dim.getFactTableList();

        boolean isPass = false;
        if (!CollectionUtils.isEmpty(factTableList) && null != dimTable) {
            for (Table factTable : factTableList) {

                if (!CollectionUtils.isEmpty(measFactTableList) && !this.hasTable(measFactTableList, factTable)) {
                    continue;
                }

                String factSchema = factTable.getSchemaName();
                String factTableName = factTable.getTableName();
                String factColumn = factTable.getFactColumn();

                String dimSchema = dimTable.getSchemaName();
                String dimTableName = dimTable.getTableName();
                String dimPrimaryKey = dimTable.getDimPrimaryKey();
                String dimColumn = dimTable.getDimColumn();

                String checkSql = "select dt. " + dimPrimaryKey + ", dt." + dimColumn + " from "
                        + factSchema + "." + factTableName + " as ft left join "
                            + dimSchema + "." + dimTableName + " as dt on ft." + factColumn + "=dt." + dimPrimaryKey + " and dt.code='" + dim.getCode() + "' limit 0";

                try {
//                    List<Map<String, Object>> list = jdbcTemplate.queryForList(checkSql);
                    isPass = true;
                } catch (Exception ex) {
                    ex.printStackTrace();
                    throw new IllegalArgumentException(this.buildErrorDimInfo(dim, "维度查询基本校验规则不通过，请联系维度负责人修复。" + ex.getMessage()));
                }

            }
        }

        if (isQuery) {
            //事实表或维度表为空，不能走sql校验。
            Preconditions.checkArgument(isPass, "维度的事实表或维度表异常，请联系维度负责人修复。dim code : " + dim.getCode() + " name:" + dim.getName() + " factTableList is " + CollectionUtils.isEmpty(factTableList) + " dimTable is " + dimTable);
        }

    }

    @Override
    public void checkDimensionJoinTableType(Dimension dimension, List<Table> measFactTableList) {

        //标准维有维表验证
        List<Table> dimTableList = dimension.getDimTableList();
        List<Table> factTableList = dimension.getFactTableList();

        TempThreadLocalUtil.set("owner", dimension.getCreator());

        boolean hasAbleDimTable = false;
        for (Table dimTable : dimTableList) {
            for (Table factTable : factTableList) {

                if (BuildSqlServiceImpl.hasFactColumnByDimTable(factTable.getMasterPrimaryKey(), dimTable.getColumnList())) {
                    hasAbleDimTable = true;
                }

                if (!CollectionUtils.isEmpty(measFactTableList) && !this.hasTable(measFactTableList, factTable)) {
                    continue;
                }

                boolean dimTableHasMasterColumn = BuildSqlServiceImpl.hasFactColumnByDimTable(factTable.getMasterPrimaryKey(), dimTable.getColumnList());
                if (!dimTableHasMasterColumn) {
                    continue;
                }

                String dimSchema = dimTable.getSchemaName();
                String dimTableName = dimTable.getTableName();
                String dimId = dimTable.getDimPrimaryKey();
                String dimColumn = dimTable.getDimColumn();

                String factSchema = factTable.getSchemaName();
                String factTableName = factTable.getTableName();
                String factFk = factTable.getFactColumn();
                String masterPrimaryKey = factTable.getMasterPrimaryKey();

                factFk = BuildSqlServiceImpl.getColumnName(factFk, "ft");

                String fk = dimId;
//                if (StringUtil.isNotEmpty(masterPrimaryKey)) {
//                    fk = masterPrimaryKey;
//                }
                fk = BuildSqlServiceImpl.setAlias(fk, "dt");
                factFk = BuildSqlServiceImpl.setAlias(factFk, "ft");

//                String checkSql = "select distinct dt." + dimId + ", dt." + dimColumn + " from " + factSchema + "." + factTableName + " as ft left join " + dimSchema + "." + dimTableName + " as dt on dt." + fk + "=ft." + factFk + " limit 0";
                String checkSql = "select distinct dt." + dimId + ", dt." + dimColumn + " from " + factSchema + "." + factTableName + " as ft left join " + dimSchema + "." + dimTableName + " as dt on " + fk + "=" + factFk + " limit 0";

                try {
//                    List<Map<String, Object>> list = jdbcTemplate.queryForList(checkSql);
                } catch (Exception ex) {
                    ex.printStackTrace();
                    throw new IllegalArgumentException(this.buildErrorDimInfo(dimension, "维度表基本校验不通过，请联系维度负责人修复。" + ex.getMessage()));
                }

            }
        }

        if (!hasAbleDimTable) {
            throw new IllegalArgumentException(this.buildErrorDimInfo(dimension, "维度表主维度并不存在与任何维度表的列值中，请联系维度负责人修复。masterPrimaryKey not exist dimTables.getColumnList()"));
        }

    }
    
    @Override
    public void checkDegenerateSql(Dimension dim, List<Table> measFactTableList) {

        TempThreadLocalUtil.set("owner", dim.getCreator());
        List<Table> factTableList = dim.getFactTableList();
        for (Table factTable : factTableList) {

            if (!CollectionUtils.isEmpty(measFactTableList) && !this.hasTable(measFactTableList, factTable)) {
                continue;
            }

            String factSchema = factTable.getSchemaName();
            String factTableName = factTable.getTableName();
            String factColumn = factTable.getFactColumn();
            String masterPrimaryKey = factTable.getMasterPrimaryKey();

            // 允许 masterPrimaryKey 与 factColumn 不同（JOIN 维度场景）
            if (!factColumn.equalsIgnoreCase(masterPrimaryKey)) {
                return;
            }

            String checkSql = "select distinct " + masterPrimaryKey + " from " + factSchema + "." + factTableName + " limit 0";

            try {
//                List<Map<String, Object>> list = jdbcTemplate.queryForList(checkSql);
            } catch (Exception ex) {
                ex.printStackTrace();
                throw new IllegalArgumentException(this.buildErrorDimInfo(dim, "退化维的基本sql校验不通过，请联系维度负责人修复。" + ex.getMessage()));
            }

        }
    }

    @Override
    public List<String> checkIDimFactTable(Set<Dimension> dimensionSet, Set<String> allDimCodeSet) {

        Map<String, List<String>> dimTableNameMap = new HashMap<String, List<String>>();
        List<String> allTableNameList = new ArrayList<String>();
        String owers = "";
        for (Dimension dimension : dimensionSet) {

            owers += "," + dimension.getCreator();

            if (!allDimCodeSet.contains(dimension.getCode())) {
                continue;
            }

            List<Table> factTableList = dimension.getFactTableList();
            List<String> tableNameList = new ArrayList<String>();
            for (Table table : factTableList) {
                //统一转小写处理
                String tableName = table.getTableName().toLowerCase();
                tableNameList.add(tableName);
                allTableNameList.add(tableName);
            }
            dimTableNameMap.put(dimension.getCode(), tableNameList);

        }

        if (dimensionSet.size() > 1) {
            Iterator<Map.Entry<String, List<String>>> entryItr = dimTableNameMap.entrySet().iterator();
            while (entryItr.hasNext()) {
                Map.Entry<String, List<String>> entry = entryItr.next();
                List<String> tableNameList = entry.getValue();
                allTableNameList.retainAll(tableNameList);
            }
        }

        owers = owers.replaceFirst(",", "");
        TempThreadLocalUtil.set("owner", owers);

        Preconditions.checkArgument(!CollectionUtils.isEmpty(allTableNameList), "维度并不存在可用事实表，请联系维度负责人修复。 dimensions not factTables intersection available.");

        return allTableNameList;
        
    }


    public String getTableSchema(Measure measure, String tableName) {
        String schema = null;
        List<Table> factTableList = measure.getFactTable();
        for (Table factTable : factTableList) {
            if (tableName.equalsIgnoreCase(factTable.getTableName())) {
                schema = factTable.getSchemaName();
            }
        }
        return schema;
    }

    @Override
    public void checkIMeasAndDimFactTable(Measure measure, List<String> dimFactTableList, Set<String> allDimCodeSet, boolean hasFilter) {

        List<Table> factTableList = measure.getFactTable();
        Set<String> measFactTableSet = new HashSet<String>();
        for (Table factTable : factTableList) {

            MeasureType measType = factTable.getMeasureType();
            if (MeasureType.ORIGIN.equals(measType)) {
                measFactTableSet.add(factTable.getTableName().toLowerCase());
            }

        }

        boolean isHave = false;

        String useFactTable = null;
        for (String measFactTable : measFactTableSet) {
            if (dimFactTableList.contains(measFactTable)) {
                isHave = true;
                useFactTable = measFactTable;
                break;
            }
        }

        TempThreadLocalUtil.set("owner", measure.getCreator());

        String meassage = " Measure factTables : " + measFactTableSet.toString() + "   Dimension factTables : " + dimFactTableList.toString();
        Preconditions.checkArgument(isHave, this.buildErrorMeasInfo(measure, "维度事实表与指标事实表并不相交，请联系指标负责人。The fact table of the measure does not exist in the dimension fact table." + meassage));

        if (!hasFilter) {
            this.checkDimValueNumber(measure, allDimCodeSet, useFactTable);
        }

    }

    /**
     * 检查维度值与模型关系。
     * @param measure
     * @param allDimCodeSet
     * @param useFactTable
     */
    private void checkDimValueNumber(Measure measure, Set<String> allDimCodeSet, String useFactTable) {

        List<DimensionHistogramRequest> dimHistogramReqList = new ArrayList<>();
        for (String dimCode : allDimCodeSet) {

            DimensionHistogramRequest dimHistogramReq = new DimensionHistogramRequest();
            dimHistogramReq.setCode(dimCode);

            String scheam = this.getTableSchema(measure, useFactTable);
            Set<String> tableSet = new HashSet<>();
            tableSet.add(scheam + "." + useFactTable);

            dimHistogramReq.setTableNames(tableSet);
            dimHistogramReqList.add(dimHistogramReq);

        }

        List<HistogramInfo> histogramInfoList = indicatorService.listDimensionHistogram(dimHistogramReqList);

        BigDecimal dimAllNum = BigDecimal.valueOf(1);
        Long MAX_ROWS = Long.valueOf(10000000);
        Long MAX_DIM_ROWS = Long.valueOf(60000000);
        Long MAX_DIM_LIMIT_ROWS = Long.valueOf(500000);
        for (HistogramInfo histogramInfo : histogramInfoList) {

            String tableName = histogramInfo.getTableName();
            Long tblRowNum = histogramInfo.getTableRowNum();

            //维值在表上的数目
            Long dimRowNum = histogramInfo.getDimensionRowNum();
            dimAllNum = dimAllNum.multiply(BigDecimal.valueOf(dimRowNum));

            //数据表值大于1千万，并且维度值大于6百万，则判定为数据过多。
            if (null != tblRowNum && (tblRowNum > MAX_ROWS && dimRowNum >= MAX_DIM_LIMIT_ROWS)) {
                Preconditions.checkArgument(false, this.buildErrorMeasInfo(measure, "维度值的可能性组合过大，请删减部分维度或增加限制条件。dim values >= MAX_DIM_LIMIT_ROWS(50W) && tblRowNum > MAX_ROWS(1000W) : " + dimRowNum));
            }

        }

        //维度值组合大于100万。
        if (dimAllNum.compareTo(BigDecimal.valueOf(MAX_DIM_ROWS)) == 1 && allDimCodeSet.size() > 10) {
            Preconditions.checkArgument(false, this.buildErrorMeasInfo(measure, "维度值的可能性组合过大，请删减部分维度或增加限制条件。DIM values Too many dimension values : " + dimAllNum));
        }

    }

    @Override
    public void checkDimension(Dimension dim, List<Table> measFactTableList) {
        this.checkDimension(dim, measFactTableList, true);
    }

    @Override
    public void checkDimension(Dimension dim, List<Table> measFactTableList, String dimCode, boolean isQuery) {
        //维度共有验证
        Set<String> codeSet = new HashSet<String>();
        codeSet.add(dimCode);
        //code
        this.checkCode(dim, codeSet);
        //name
        this.checkName(dim);
        if (isQuery) {
            //维度必须拥有事实表
            this.checkDimensionFactTable(dim);
        }

        //标准维验证
        if (DimType.STD_WITH_TABLE.equals(dim.getDimType())) {
            // 2.3 标准维有维表必须含有维度表
            this.checkDimensionDimTable(dim);
            if (isQuery) {
                // 2.4 标准维维度表，维度主键必须与事实表外键外键一致,保证语法可链。
                this.checkDimensionJoinTableType(dim, measFactTableList);
            }

        } else if (DimType.DEGENERATE_DIM.equals(dim.getDimType())) {
            // 检查退化维sql验证
            this.checkDegenerateSql(dim, measFactTableList);
        } else if (DimType.STD_WITHOUT_TABLE.equals(dim.getDimType())) {
            // 标准维无维表必须含有维度表
            this.checkDimensionDimTable(dim);
            // 标准维无维表sql验证
            this.checkNoDimTableSql(dim, measFactTableList, isQuery);
        }
    }

    public void checkDimension(Dimension dim, List<Table> measFactTableList, boolean isQuery) {
        this.checkDimension(dim, measFactTableList, "", isQuery);
    }

    @Override
    public void checkMeasure(Measure measure) {

        TempThreadLocalUtil.set("owner", measure.getCreator());
        //1.2 指标必须有依赖的事实表
        this.checkMeasureFactTable(measure);
        MeasureType measType = measure.getMeasType();
        if (MeasureType.ORIGIN.equals(measType)) {
            //原子指标
            //1.3 原子指标必须含有聚合方式
            this.checkMeasureExpression(measure);
            //1.4 基础指标验证是否执行，规避sum(字符、日期、blob等非法字段)
            this.checkMeasureExprRun(measure);
        } else if (MeasureType.DERIVED.equals(measType)) {
            //衍生指标|复合指标
            this.checkStr(measure.getExpression(), this.buildErrorMeasInfo(measure, "表达式为空，请联系指标负责人修复。 expression is null"));
            this.checkCollection(measure.getExpList(), this.buildErrorMeasInfo(measure, "表达式为空，请联系指标负责人修复。 expList is null"));

            Set<Measure> hasAllMeasureSet = measure.getHasAllMeasureSet();
            this.checkCollection(hasAllMeasureSet, this.buildErrorMeasInfo(measure, "复合或衍生指标下面无子指标，请联系指标负责人修复。 hasAllMeasureSet is null"));

            for (Measure sonMeas : hasAllMeasureSet) {
                this.checkMeasure(sonMeas);
            }

        } else if (MeasureType.EXTENDED.equals(measType)) {

            //派生指标
            this.checkStr(measure.getExpression(), this.buildErrorMeasInfo(measure, "表达式为空，请联系指标负责人修复。 expression is null"));
            this.checkCollection(measure.getExpList(), this.buildErrorMeasInfo(measure, "表达式为空，请联系指标负责人修复。 expList is null"));

            //派生指标维度筛选条件
            Table useTempFactTable = measure.getUseTempFactTable();
            this.checkObj(useTempFactTable, this.buildErrorMeasInfo(measure, "指标没有找到可以使用的事实表，请联系指标负责人修复。useTempFactTable is null"));

            List<Filter> filterList = useTempFactTable.getFilterList();
            this.checkCollection(filterList, this.buildErrorMeasInfo(measure, "衍生指标无筛洗过滤条件，请联系指标负责人修复。filterList is null"));

            Set<Measure> hasAllMeasureSet = measure.getHasAllMeasureSet();
            this.checkCollection(hasAllMeasureSet, this.buildErrorMeasInfo(measure, "衍生指标无依赖的指标，请联系指标负责人修复。hasAllMeasureSet is null"));

            for (Measure sonMeas : hasAllMeasureSet) {
                this.checkMeasure(sonMeas);
            }

        }

    }

    @Override
    public void checkIndicatorInfo(IndicatorTuple indicatorTuple, Set<String> allDimCodeSet, Set<String> allMeasCodeSet, QueryParam queryParam) {

        /**
         * 1. 指标基本校验
         *     1.1 指标不允许为空
         *     1.2 原子指标必须有依赖的事实表
         *     1.3 原子指标必须聚合方式
         *     1.4 基础指标验证是否执行，规避sum(字符、日期、blob等非法字段)
         *
         * 2. 维度基本校验
         *     2.1 维度不允许为空
         *     2.2 标准维 有维表必须含有维度表和事实表
         *     2.3 维度维度表、事实表必须可用
         *     2.4 标准维维度表，维度主键必须与事实表外键外键一致
         *     2.5 所有维度必须存在共有事实表
         *     2.6 退化维 必须含有事实表
         * 3. 指标所含事实表必须与维度的共有事实表有交集
         */

        /**
         *  1. 指标基本校验开始
         */
        //1.1 指标不允许为空
        Set<Measure> measureSet = indicatorTuple.getMeasureSet();
        this.checkMeasureNull(measureSet);

        List<Table> measFactTableList = new LinkedList<>();

        //迭代指标
        for (Measure measure : measureSet) {
            this.checkMeasure(measure);
            measFactTableList.addAll(measure.getFactTable());
        }

        /**
         * 2. 维度基本校验
         */
        Set<Dimension> dimensionSet = indicatorTuple.getDimensionSet();
        if (!CollectionUtils.isEmpty(dimensionSet)) {
            //2.1 维度不允许为空
//            this.checkDimensionNull(dimensionSet);
            for (Dimension dim : dimensionSet) {
                TempThreadLocalUtil.set("owner", dim.getCreator());
                //维度验证
                this.checkDimension(dim, measFactTableList);
            }

            // 2.5 所有维度必须存在共有事实表
            List<String> dimFactTableList = this.checkIDimFactTable(dimensionSet, allDimCodeSet);

            boolean hasFilter = !CollectionUtils.isEmpty(queryParam.getFilterList());
            /**
             * 3. 原生指标所含事实表必须与维度的共有事实表有交集
             */
            this.checkMeasFactTable(measureSet, dimFactTableList, allDimCodeSet, hasFilter);
//            for (Measure measure : measureSet) {
//                MeasureType measType = measure.getMeasType();
//                if (MeasureType.ORIGIN.equals(measType)) {
//                    this.checkIMeasAndDimFactTable(measure, dimFactTableList);
//                } else {
//                    Set<Measure> hasAllMeasureSet = measure.getHasAllMeasureSet();
//
//                }
//            }
        }

    }

    private void checkMeasFactTable(Set<Measure> measureSet, List<String> dimFactTableList, Set<String> allDimCodeSet, boolean hasFilter) {
        for (Measure measure : measureSet) {
            MeasureType measType = measure.getMeasType();
            if (MeasureType.ORIGIN.equals(measType)) {
                this.checkIMeasAndDimFactTable(measure, dimFactTableList, allDimCodeSet, hasFilter);
            } else {
                Set<Measure> hasAllMeasureSet = measure.getHasAllMeasureSet();
                this.checkMeasFactTable(hasAllMeasureSet, dimFactTableList, allDimCodeSet, hasFilter);
            }
        }
    }

    private void isNull(Object obj, String message) {
        Preconditions.checkArgument(null != obj, message);
    }


    @Override
    public void checkName(Dimension dim) {
        this.isNull(dim, "维度为空 dim is Null");
        Preconditions.checkArgument(StringUtil.isNotEmpty(dim.getName()), this.buildErrorDimInfo(dim, "维度名称为空，请联系维度负责人。The dim Name is Null:" + dim));
    }

    @Override
    public void checkCode(Dimension dim, Set<String> allDimCodeSet) {
        this.isNull(dim, "dim is Null codes:" + allDimCodeSet);
        Preconditions.checkArgument(StringUtil.isNotEmpty(dim.getCode()), this.buildErrorDimInfo(dim, "维度code为空，请联系维度负责人。git The dim code is Null:" + dim));
    }
    

    
}
