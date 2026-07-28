package com.graphinsight.indicator.util.contribution;

import com.graphinsight.indicator.util.contribution.bean.ContributionCalculationParam;
import com.graphinsight.indicator.util.contribution.bean.ContributionCalculationResult;

import java.math.BigDecimal;

/**
 * Date: 2022/6/14
 * Desc:除法策略
 * 计算Y = A / B 时 A、B对Y的贡献度
 */
public class DivisionStrategy extends ContributionStrategy {
    @Override
    public BigDecimal calculateContribution(ContributionCalculationParam param) {
        BigDecimal a1 = param.getCurrentPeriodValue();
        BigDecimal a0 = param.getPreviousPeriodValue();
        BigDecimal b1 = param.getCurrentPeriodOppositeValue();
        BigDecimal b0 = param.getPreviousPeriodOppositeValue();

        if (param.isDividend()){
            // 被除数 也就是A
            if (BigDecimal.ZERO.compareTo(b0) == 0){
                return null;
            }
            return a1.divide(b0,10,BigDecimal.ROUND_DOWN).subtract(a0.divide(b0,10,BigDecimal.ROUND_DOWN));
        } else {
            // 除数 也就是B
            if (BigDecimal.ZERO.compareTo(b1) == 0 || BigDecimal.ZERO.compareTo(b0) == 0){
                return null;
            }
            return b0.divide(a1,10,BigDecimal.ROUND_DOWN).subtract(b0.divide(a0,10,BigDecimal.ROUND_DOWN));
        }
    }

    public static void main(String[] args) {
        DivisionStrategy divisionStrategy = new DivisionStrategy();
        ContributionCalculationParam.ContributionCalculationParamBuilder builder = ContributionCalculationParam.builder();
        builder.upperLayerPreviousPeriodValue(new BigDecimal(0.3));
        builder.upperLayerCurrentPeriodValue(new BigDecimal(0.4));
        builder.previousPeriodValue(new BigDecimal(6));
        builder.currentPeriodValue(new BigDecimal(4));
        builder.currentPeriodOppositeValue(new BigDecimal(10));
        builder.previousPeriodOppositeValue(new BigDecimal(20));
        builder.isRatio(true);
        builder.dividend(true);
        ContributionCalculationParam a = builder.build();


        ContributionCalculationParam.ContributionCalculationParamBuilder builder1 = ContributionCalculationParam.builder();
        builder1.upperLayerPreviousPeriodValue(new BigDecimal(0.3));
        builder1.upperLayerCurrentPeriodValue(new BigDecimal(0.4));
        builder1.previousPeriodValue(new BigDecimal(20));
        builder1.currentPeriodValue(new BigDecimal(10));
        builder1.currentPeriodOppositeValue(new BigDecimal(4));
        builder1.previousPeriodOppositeValue(new BigDecimal(6));
        builder1.dividend(false);
        builder.isRatio(true);
        ContributionCalculationParam b = builder1.build();

        ContributionCalculationResult resultA = divisionStrategy.calculate(a);
        ContributionCalculationResult resultB = divisionStrategy.calculate(b);

        System.out.println("A -- " + resultA.getContributionValue());
        System.out.println("B -- " + resultB.getContributionValue());
    }

}
