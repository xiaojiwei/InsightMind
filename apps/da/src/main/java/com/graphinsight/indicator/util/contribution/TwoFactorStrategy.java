package com.graphinsight.indicator.util.contribution;

import com.graphinsight.indicator.util.contribution.bean.ContributionCalculationParam;
import com.graphinsight.indicator.util.contribution.bean.RatioMeasureCalculationParam;

import java.math.BigDecimal;

/**
 * Author: lixiaolong
 * Date: 2022/6/14
 * Desc: 默认策略
 */
public class TwoFactorStrategy extends ContributionStrategy {
    @Override
    public BigDecimal calculateContribution(ContributionCalculationParam param) {

        RatioMeasureCalculationParam ratioParam = param.getRatioParam();
        BigDecimal Y0 = ratioParam.getY0() == null ? BigDecimal.ZERO : ratioParam.getY0();
        BigDecimal B0 = ratioParam.getB_base_total();
        BigDecimal B1 = ratioParam.getB_current_total();
        BigDecimal Bi1 = ratioParam.getB_currentValue();
        BigDecimal Bi0 = ratioParam.getB_baseValue();
        BigDecimal Ai0 = ratioParam.getA_baseValue();
        BigDecimal Ai1 = ratioParam.getA_currentValue();

        if (Bi0.compareTo(BigDecimal.ZERO) == 0 ||
                Bi1.compareTo(BigDecimal.ZERO) == 0 ||
                B0.compareTo(BigDecimal.ZERO) == 0 ||
                B1.compareTo(BigDecimal.ZERO) == 0 ){

            return null;
        }

        /**
         * Axi = (Ai1 / Bi1 - Ai0 / Bi0) * (Bi0 / B0 )
         */
        BigDecimal Axi = Ai1.divide(Bi1, 10, BigDecimal.ROUND_DOWN)
                .subtract(
                        Ai0.divide(Bi0, 10, BigDecimal.ROUND_DOWN))
                .multiply(Bi0.divide(B0, 10, BigDecimal.ROUND_DOWN));


        /**
         * Bxi = (Bi1 / B1 - Bi0 / B0) * (Ai1 / Bi1 - Y0)
         */
        BigDecimal Bxi = Bi1.divide(B1, 10, BigDecimal.ROUND_DOWN)
                .subtract(
                        Bi0.divide(B0, 10, BigDecimal.ROUND_DOWN))
                .multiply(Ai1.divide(Bi1, 10, BigDecimal.ROUND_DOWN)
                        .subtract(Y0));

        return Axi.add(Bxi);
    }
}
