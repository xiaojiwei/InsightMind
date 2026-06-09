package com.graphinsight.indicator.model;

import lombok.Data;

@Data
public class Where {

    /**
     * 维度code
     */
    private String dimCode;

    /**
     * 维度所选值
     */
    private String dimValues;

    /**
     * 操作类型 and or
     */
    private String operator;

}
