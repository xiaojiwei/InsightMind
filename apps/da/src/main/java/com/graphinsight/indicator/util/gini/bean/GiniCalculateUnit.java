package com.graphinsight.indicator.util.gini.bean;

import com.graphinsight.indicator.exception.GiniCalculationException;
import com.graphinsight.indicator.util.MathUtil;
import lombok.Data;

import java.util.Objects;

/**
 * Date: 2022/7/12
 * Desc:
 */
@Data
public class GiniCalculateUnit {

    public GiniCalculateUnit(double baseValue, double currentValue,double deltaTotalAbsValue, double totalBaseValue) throws GiniCalculationException {
        this.baseValue = baseValue;
        this.currentValue = currentValue;
        this.totalBaseValue = totalBaseValue;
        this.deltaTotalAbsValue = deltaTotalAbsValue;
        init();
    }

    private void init() throws GiniCalculationException {
        if (Objects.equals(totalBaseValue,0.0)){
            throw GiniCalculationException.error("基期值总和为0，无法计算基尼系数");
        }
        if (Objects.equals(deltaTotalAbsValue,0.0)){
            throw GiniCalculationException.error("波动绝对值累加为0，无法计算基尼系数");
        }
        // 波动绝对值
        this.deltaAbsValue = Math.abs(baseValue - currentValue);
        // 波动占比
        this.deltaValueRate = deltaAbsValue / deltaTotalAbsValue;
        // 基期占比
        this.baseValueRate = baseValue / totalBaseValue;
        this.sortBy = MathUtil.sigmoid(deltaValueRate) / MathUtil.sigmoid(baseValueRate);
        this.baseAbsValue = Math.abs(baseValue);
        this.currentAbsValue = Math.abs(currentValue);
    }

    /**
     * 当期值
     */
    private double currentValue;

    /**
     * 基期值
     */
    private double baseValue;

    /**
     * 基期绝对值
     */
    private double baseAbsValue;

    /**
     * 本期绝对值
     */
    private double currentAbsValue;

    /**
     * 基期总和
     */
    private double totalBaseValue;

    /**
     * 波动累计值
     */
    private double deltaTotalAbsValue;

    /**
     * 本期总和
     */
    private double totalCurrentValue;

    /**
     * 波动绝对值
     */
    private double deltaAbsValue;

    /**
     * 波动占比
     */
    private double deltaValueRate;

    /**
     * 基期占比
     */
    private double baseValueRate;

    /**
     * 排序依据
     * 波动占比/基期占比
     */
    private double sortBy;

}
