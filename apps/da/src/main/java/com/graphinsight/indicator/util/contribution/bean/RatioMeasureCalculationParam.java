package com.graphinsight.indicator.util.contribution.bean;

import lombok.Data;

import java.math.BigDecimal;

/**
 * Author: lixiaolong
 * Date: 2022/8/4
 * Desc:
 */
@Data
public class RatioMeasureCalculationParam {

    /**
     * A(分子)基期值
     */
    private BigDecimal a_baseValue;

    /**
     * B(分母)基期值
     */
    private BigDecimal b_baseValue;

    /**
     *  A(分子)本期值
     */
    private BigDecimal a_currentValue;

    /**
     * B(分母)本期值
     */
    private BigDecimal b_currentValue;

    /**
     * 分母总和
     */
    private BigDecimal b_current_total;

    /**
     * 分子总和
     */
    private BigDecimal a_current_total;

    /**
     * 分母总和
     */
    private BigDecimal b_base_total;

    /**
     * 分子总和
     */
    private BigDecimal a_base_total;

    /**
     * 基期总和
     */
    private BigDecimal Y0;

}
