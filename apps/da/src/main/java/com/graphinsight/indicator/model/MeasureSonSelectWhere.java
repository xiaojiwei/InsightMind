package com.graphinsight.indicator.model;

import lombok.Data;

@Data
public class MeasureSonSelectWhere {

    /**
     * 操作符 and or
     */
    private String operator = "and";

    /**
     * 别名+列名
     */
    private String colume;

    /**
     * 值
     */
    private String[] values;



}
