package com.graphinsight.indicator.util.contribution;

import com.graphinsight.indicator.util.contribution.bean.ContributionCalculationParam;

import java.math.BigDecimal;

/**
 * Author: lixiaolong
 * Date: 2022/6/14
 * Desc:
 */
public class MultiplicationStrategy extends ContributionStrategy {

    /**
     * 乘法拆解
     * @param param
     * @return
     */
    @Override
    public BigDecimal calculateContribution(ContributionCalculationParam param) {
        BigDecimal upperLayerDeltaValue = calculateUpperLayerDeltaValue(param);
        if (param.getUpperLayerCurrentPeriodValue().doubleValue() == 0.0 || param.getUpperLayerPreviousPeriodValue().doubleValue() == 0){
               return null;
        }
        //分母
        BigDecimal denominator = BigDecimal.valueOf(Math.log(param.getUpperLayerCurrentPeriodValue().doubleValue()) - Math.log(param.getUpperLayerPreviousPeriodValue().doubleValue()));
        // 分子
        if (BigDecimal.ZERO.compareTo(param.getPreviousPeriodValue()) == 0 || BigDecimal.ZERO.compareTo(denominator) == 0){
            return null;
        }
        BigDecimal molecular = BigDecimal.valueOf(Math.log(param.getCurrentPeriodValue().divide(param.getPreviousPeriodValue(),10,BigDecimal.ROUND_DOWN).doubleValue()));

        BigDecimal divide = upperLayerDeltaValue.multiply(molecular).divide(denominator, 10, BigDecimal.ROUND_DOWN);
        if (param.isParentPatio()){
            return divide;
        }
        return divide.divide(param.getUpperLayerPreviousPeriodValue(),10,BigDecimal.ROUND_DOWN);
    }

}
