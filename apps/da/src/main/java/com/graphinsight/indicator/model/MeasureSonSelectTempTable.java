package com.graphinsight.indicator.model;

import lombok.Data;

import java.util.LinkedList;
import java.util.List;

@Data
public class MeasureSonSelectTempTable {

    /**
     * 唯一id
     */
    private String id;

    /**
     * 唯一code
     */
    private Measure measure;

    /**
     * 派生指标code
     */
    private String exMeasCode;

    /**
     * 别名+column
     */
    private String column;

    /**
     *  column as Name
     */
    private String asName;

    /**
     *  table as Name
     */
    private String asAlias;

    /**
     * 补0位的字段
     */
    private boolean isFillNull;

    /**
     * 指标子查询sql
     */
    private String sonSelectSql;

    /**
     * 子查询中指标
     */
    private String sonSelectMeasureColumn;

    /**
     * 指标平台配置sql语句
     */
    private String whereCondition;

    /**
     * 派生指标依赖的过滤条件
     */
    private List<MeasureSonSelectWhere> whereList = new LinkedList<MeasureSonSelectWhere>();

    /**
     * 派生指标依赖的过滤条件
     */
    private List<Filter> exFilterList = new LinkedList<>();

    /**
     * 指标需要的筛选维度
     */
    private List<Filter> measFilterList = new LinkedList<>();

}
