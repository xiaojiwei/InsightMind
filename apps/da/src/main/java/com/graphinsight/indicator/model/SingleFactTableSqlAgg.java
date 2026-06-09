package com.graphinsight.indicator.model;

import com.graphinsight.indicator.enums.SourceType;
import lombok.Data;

import java.util.*;

@Data
public class SingleFactTableSqlAgg {

    /**
     * 表上所有列
     */
    private List<String> columnList = new ArrayList<String>();

    /**
     * 表上所有维度列名称
     */
    private List<String> dimColumnList = new ArrayList<String>();

    /**
     * 表上所有维度列名称
     */
    private List<String> dimColumnNameList = new ArrayList<String>();

    /**
     * 需要统计全部的维度
     */
    private List<String> statsAllColumnList = new ArrayList<>();

    /**
     * 标上所有基础指标
     */
    private List<String> measureList = new ArrayList<String>();

    /**
     * @see com.graphinsight.indicator.enums.SortType
     */
    private SourceType source;

    /**
     * db
     */
    private String schema;

    /**
     * 事实表名
     */
    private String name;

    /**
     * 别名
     */
    private String alias;

    /**
     * 数据来源from，独立与表名，为兼容level2
     */
    private String from;

    /**
     *  表table
     */
    private Table table;

    /**
     * 筛选条件
     */
    private List<Filter> whereFilterList = new ArrayList<Filter>();

    /**
     * 分组条件
     */
    private List<String> groupByList = new ArrayList<String>();

    private List<String> groupingSetsList = new ArrayList<>();

    /**
     * 关联的维度表
     */
    private List<LeftJoinDimTable> leftJoinedDimTableList = new LinkedList<>();

    /**
     * 维度
     */
    private Set<Dimension> dimensionSet = new LinkedHashSet<Dimension>();

    /**
     * 分组维度
     */
    private Set<Dimension> groupDimSet = new LinkedHashSet<Dimension>();

    /**
     * 指标
     */
    private Set<Measure> measureSet = new LinkedHashSet<Measure>();


    /**
     * 计算指标集合
     */
    private Set<MeasureSonSelectTempTable> measureSonSelectTempTableSet = new LinkedHashSet<MeasureSonSelectTempTable>();

    /**
     * 事实表补位的指标
     */
    private Set<String> exFillNullMeasureSet = new LinkedHashSet<String>();

    /**
     * 表是重的维度是否处理过
     */
    private Map<String, String> tableUseMap = new HashMap<>();

}
