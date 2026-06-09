package com.graphinsight.indicator.model;

import lombok.Data;

/**
 * 无维表
 */
@Data
public class DimValue {

    /**
     * 维度code
     */
    private String code;

    /**
     * 值
     */
    private String value;

    /**
     * 描述
     */
    private String description;

}
