package com.graphinsight.indicator.model.vo;

import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;

/**
 * Author: lixiaolong
 * Date: 2022/11/4
 * Desc:
 */
@Data
public class DismantlingTreeNodeData implements Serializable {

    /**
     * 占比
     */
    @ApiModelProperty(value = "基期占比")
    private BigDecimal baseProportion = BigDecimal.ZERO;

    /**
     * 本期占比
     */
    @ApiModelProperty(value = "本期占比")
    private BigDecimal currentProportion = BigDecimal.ZERO;

    /**
     * 波动值
     */
    @ApiModelProperty(value = "波动值")
    private BigDecimal deltaValue;

    /**
     * 波动比率
     */
    @ApiModelProperty(value = "波动比率,如果为null，显示 '-' ")
    private BigDecimal deltaValueRate;

    /**
     * 对上层指标贡献度
     */
    @ApiModelProperty(value = "对上层指标的贡献值,如果为null，显示 '-' ")
    private BigDecimal relativelyContributionValue = BigDecimal.ZERO;


    /**
     * 子指标对上层指标贡献度总和
     */
    @ApiModelProperty(value = "对上层指标的贡献值,如果为null，显示 '-' ")
    private BigDecimal relativelyContributionTotal = BigDecimal.ZERO;


    /**
     * 对根指标贡献度
     */
    @ApiModelProperty(value = "对根指标的贡献度,如果为null，显示 '-' ")
    private BigDecimal absoluteContributionValue = BigDecimal.ZERO;

    /**
     * 对根指标贡献度
     */
    @ApiModelProperty(value = "对根指标的贡献度占比,如果为null，显示 '-' ")
    private BigDecimal absoluteContributionValueRate;

    /**
     * 对根指标贡献度
     */
    @ApiModelProperty(value = "相对贡献度占比,如果为null，显示 '-' ")
    private BigDecimal relativelyContributionValueRate = BigDecimal.ZERO;


    /**
     * 上期的值
     */
    @ApiModelProperty(value = "上(基)期值")
    private BigDecimal basePeriodValue = BigDecimal.ZERO;

    /**
     * 本期的值
     */
    @ApiModelProperty(value = "本期值")
    private BigDecimal currentPeriodValue = BigDecimal.ZERO;

    /**
     * 上期的值
     */
    @ApiModelProperty(value = "上层节点上(基)期值")
    private BigDecimal upperLayerBasePeriodValue = BigDecimal.ZERO;

    /**
     * 本期的值
     */
    @ApiModelProperty(value = "上层节点本期值")
    private BigDecimal upperLayerCurrentPeriodValue = BigDecimal.ZERO;

    /**
     * 分子指标的值
     */
    private BigDecimal molecularValue;

    /**
     * 分子指标的总和
     */
    private BigDecimal molecularValueTotal;

    /**
     * 分母指标的值
     */
    private BigDecimal denominatorValue;

    /**
     * 分母指标的总和
     */
    private BigDecimal denominatorValueTotal;


    /**
     * 分子指标的当期值
     */
    private BigDecimal currentMolecularValue;

    /**
     * 分母指标的当期值
     */
    private BigDecimal currentDenominatorValue;

    /**
     * 分子指标的基期值
     */
    private BigDecimal baseMolecularValue;

    /**
     * 分母指标的基期值
     */
    private BigDecimal baseDenominatorValue;

    /**
     * 当期分母指标的总和
     */
    private BigDecimal currentDenominatorValueTotal;

    /**
     * 当期期分子指标的总和
     */
    private BigDecimal currentMolecularValueTotal;

    /**
     * 基期分母指标的总和
     */
    private BigDecimal baseDenominatorValueTotal;

    /**
     * 基期分子指标的总和
     */
    private BigDecimal baseMolecularValueTotal;



    /**
     * 当前节点下钻之后的节点基期之和
     * 注意跟上层节点的值是不一样的，因为上层节点和本层节点依赖的计算指标可能不一样
     */
    private BigDecimal baseTotal = BigDecimal.ZERO;

    /**
     * 当前节点下钻之后的节点当期之和
     */
    private BigDecimal currentTotal = BigDecimal.ZERO;

}
