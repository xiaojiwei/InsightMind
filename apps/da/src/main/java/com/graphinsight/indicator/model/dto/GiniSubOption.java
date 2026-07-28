package com.graphinsight.indicator.model.dto;

import lombok.Data;

/**
 * Date: 2022/7/6
 * Desc: 基尼系数维度下的分项值
 */
@Data
public class GiniSubOption {

    /**
     * 维值对应的ID
     */
    private String dimValueId;

    /**
     * 维值对应的名称
     */
    private String dimValueName;

    /**
     * 当期值
     */
    private double currentValue;

    /**
     * 基期值
     */
    private double baseValue;
}
