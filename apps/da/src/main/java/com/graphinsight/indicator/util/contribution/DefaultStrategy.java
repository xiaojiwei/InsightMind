package com.graphinsight.indicator.util.contribution;

import com.graphinsight.indicator.util.contribution.bean.ContributionCalculationParam;

import java.math.BigDecimal;

/**
 * Date: 2022/6/14
 * Desc: 默认策略
 */
public class DefaultStrategy extends ContributionStrategy {
    @Override
    public BigDecimal calculateContribution(ContributionCalculationParam param) {
        return null;
    }
}
