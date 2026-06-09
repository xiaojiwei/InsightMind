package com.graphinsight.indicator.service;

import com.graphinsight.indicator.model.*;

import java.util.List;
import java.util.Set;

/**
 * sql验证服务类
 */
public interface SqlCheckService {

    /**
     * 检查指标集合是否为空
     *
     * @param measures
     * @return
     */
    void checkMeasureNull(Set<Measure> measures);

    /**
     * 原子指标必须有依赖的事实表
     *
     * @param measure
     */
    void checkMeasureFactTable(Measure measure);

    /**
     * 原子指标必须聚合方式
     *
     * @param measure
     */
    void checkMeasureExpression(Measure measure);

    /**
     * 基础指标验证是否执行，规避sum(字符、日期、blob等非法字段)
     *
     * @param measure
     */
    void checkMeasureExprRun(Measure measure);

    /**
     * 检查维度集合是否为空
     *
     * @param dimensionSet
     * @return
     */
    void checkDimensionNull(Set<Dimension> dimensionSet);

    /**
     * 维度必须有依赖的事实表
     *
     * @param dimension
     */
    void checkDimensionFactTable(Dimension dimension);

    /**
     * 检查维度的维度表
     *
     * @param dimension
     */
    void checkDimensionDimTable(Dimension dimension);

    /**
     * 标准维维度表，维度主键必须与事实表外键外键一致,保证语法可链
     *
     * @param dimension
     */
    void checkDimensionJoinTableType(Dimension dimension, List<Table> measFactTableList);

    /**
     * 检查共有维度的维度表
     *
     * @param dimensionSet
     */
    List<String> checkIDimFactTable(Set<Dimension> dimensionSet, Set<String> allDimCodeSet);

    /**
     * 检查指标和维度是否有事实表上的交集
     *
     * @param measure
     * @param dimFactTableList
     */
    void checkIMeasAndDimFactTable(Measure measure, List<String> dimFactTableList, Set<String> allDimCodeSet, boolean hasFilter);

    /**
     * 检查指标平台返回的信息完成度
     * 1. 指标基本校验
     * 1.1 指标不允许为空
     * 1.2 原子指标必须有依赖的事实表
     * 1.3 原子指标必须聚合方式
     * 1.4 基础指标验证是否执行，规避sum(字符、日期、blob等非法字段)
     * <p>
     * 2. 维度基本校验
     * 2.1 维度不允许为空
     * 2.2 标准维有维表必须含有维度表和事实表
     * 2.3 维度维度表、事实表必须可用
     * 2.4 标准维维度表，维度主键必须与事实表外键外键一致
     * 2.5 所有维度必须存在共有事实表
     * 3. 指标所含事实表必须与维度的共有事实表有交集
     *
     * @param indicatorTuple
     */
    void checkIndicatorInfo(IndicatorTuple indicatorTuple, Set<String> allDimCodeSet, Set<String> allMeasCodeSet, QueryParam queryParam);

    /**
     * 检查维度名称
     *
     * @param obj
     * @param message
     */
    void checkObj(Object obj, String message);

    /**
     * 检查字符是否为空
     *
     * @param value
     * @param errorInfo
     */
    void checkStr(String value, String errorInfo);

    /**
     * 检查维度名称
     *
     * @param dim
     */
    void checkName(Dimension dim);

    /**
     * 检查维度code
     *
     * @param dim
     */
    void checkCode(Dimension dim, Set<String> allDimCodeSet);

    /**
     * 退化维sql验证
     *
     * @param dim
     */
    void checkDegenerateSql(Dimension dim, List<Table> measFactTableList);

    /**
     * 标准维无维表sql验证
     *
     * @param dim
     */
    void checkNoDimTableSql(Dimension dim, List<Table> measFactTableList, boolean isQuery);

    /**
     * 检查指标可用事实表不能为null
     *
     * @param meas
     */
    void checkFactTable(Measure meas, Set<Table> tableSet, Set<Dimension> dimSet);

    /**
     * 校验维度
     *
     * @param dim
     */
    void checkDimension(Dimension dim, List<Table> measFactTableList);

    /**
     * 检查指标
     *
     * @param measure
     */
    void checkMeasure(Measure measure);

    /**
     * 维度校验
     *
     * @param dim
     * @param isQuery 是否查询场景
     */
    void checkDimension(Dimension dim, List<Table> measFactTableList, boolean isQuery);

    /**
     * 检查维度
     *
     * @param dim
     * @param dimCode
     * @param isQuery
     */
    void checkDimension(Dimension dim, List<Table> measFactTableList, String dimCode, boolean isQuery);

}
