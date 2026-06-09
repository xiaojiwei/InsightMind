package com.graphinsight.indicator.util.contribution.bean;

import lombok.Data;

import java.math.BigDecimal;

/**
 * Author: lixiaolong
 * Date: 2022/6/14
 * Desc:
 */
@Data
public class ContributionCalculationResult {

    /**
     * 波动值
     */
    private BigDecimal deltaValue;

    /**
     * 波动比率
     */
    private BigDecimal deltaValueRate;

    /**
     * 对上层指标贡献度
     */
    private BigDecimal contributionValue;


    /**
     * 上期的值
     */
    private BigDecimal previousPeriodValue;

    /**
     * 本期的值
     */
    private BigDecimal currentPeriodValue;

    /**
     * 贡献占比
     */
    private BigDecimal contributionValueRate;
}
