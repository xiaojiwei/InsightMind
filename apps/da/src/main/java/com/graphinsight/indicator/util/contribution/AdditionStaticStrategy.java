package com.graphinsight.indicator.util.contribution;

import com.graphinsight.indicator.util.contribution.bean.ContributionCalculationParam;

import java.math.BigDecimal;

/**
 * Date: 2022/6/14
 * Desc: 加法策略
 */
public class AdditionStaticStrategy extends ContributionStrategy {
    @Override
    public BigDecimal calculateContribution(ContributionCalculationParam param) {
        BigDecimal deltaValue = calculateDeltaValue(param);
        if (BigDecimal.ZERO.doubleValue() == param.getUpperLayerPreviousPeriodValue().doubleValue()){
            return null;
        }
        if (param.isParentPatio()){
            return deltaValue;
        }
        return  deltaValue.divide(param.getUpperLayerPreviousPeriodValue(),10,BigDecimal.ROUND_DOWN);
    }
}
