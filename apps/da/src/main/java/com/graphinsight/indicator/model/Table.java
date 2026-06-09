package com.graphinsight.indicator.model;

import com.graphinsight.indicator.auto.entity.DwColumn;
import com.graphinsight.indicator.enums.MeasureType;
import com.graphinsight.indicator.enums.SourceType;
import com.graphinsight.indicator.enums.ViewType;
import lombok.Data;

import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;

@Data
public class Table extends BaseModel {

    /**
     * @see SourceType
     *     MYSQL(0, "MySQL"),
     *     DORIS(1, "Doris");
     */
    private SourceType sourceType;

    /**
     * schema
     */
    private String schemaName;

    /**
     * tableName
     */
    private String tableName;

    /**
     * tableDescription
     */
    private String tableDescription;

    /**
     * factColumn 维度时间为事实表中的主键，指标时为度量值的列。
     */
    private String factColumn;

    /**
     * type 数据类型
     */
    private String factColumnType;

    /**
     * 维度表中主键
     */
    private String dimPrimaryKey;

    /**
     * 维度表中列名称与 dimPrimaryKey 成对出现
     */
    private String dimColumn;

    /**
     * 维度表中用于分组的 SQL 表达式（支持 CASE WHEN 等），使用 {d} 占位符代表维表别名。
     * 设置后将替代 dimPrimaryKey 作为事实子查询的 GROUP BY 键，同时跳过外层 display JOIN。
     */
    private String dimColumnExpr;

    /**
     * 主维度的viewType
     */
    private ViewType masterDimensionViewType;

    /**
     * 杂项维 ： false 次维度 true 主维度
     */
    private boolean isMaster = false;

    /**
     * 筛选条件
     */
    private String whereCondition;

    /**
     * 指标应用类型  指标应用类型 0-原生 1-衍生 2-派生
     * @see MeasureType
     *     ORIGIN(0, "原生指标"),
     *     DERIVED(1, "衍生指标"),
     *     EXTENDED(2, "派生指标");
     *
     */
    private MeasureType measureType;

    /**
     * 原生指标聚合表达式或衍生指标AST表达式
     */
    private String expression;

    /**
     * 维度时关系表时，此为主维度的主键
     * 如果维度存在维表，该字段就是维表中的维度key
     */
    private String masterPrimaryKey;

    /**
     * 是否包含columnDT
     */
    private Boolean hasColumnDT = false;

    /**
     * 为指标所属事实表时存在，指标应用类型。
     */
    private MeasureType applyType;

    /**
     * 基础指标、衍生指标、派生指标的定义公式。
     */
    private List<OperationItem> expList;

    /**
     * 派生指标条件
     */
    private List<Filter> filterList = new LinkedList<>();

    /**
     * 表所包含的字段
     */
    private List<DwColumn> columnList = new LinkedList<>();

    /**
     * 派生指标、衍生指标所依赖的下级指标
     */
    private Set<Measure> hasAllMeasureSet = new LinkedHashSet<>();

    /**
     * 派生指标所依赖的所有维度
     */
    private Set<Dimension> hasAllDimensionSet = new LinkedHashSet<>();

    /**
     * 数据库连接信息，从知识图谱读取，用于动态建立 JDBC 连接。
     * 为 null 时降级使用默认数据源。
     */
    private DataConnection connection;

}
