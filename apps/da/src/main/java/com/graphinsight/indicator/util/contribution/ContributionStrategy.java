package com.graphinsight.indicator.util.contribution;

import com.graphinsight.indicator.util.contribution.bean.ContributionCalculationParam;
import com.graphinsight.indicator.util.contribution.bean.ContributionCalculationResult;

import java.math.BigDecimal;
import java.util.Objects;

/**
 * Author: lixiaolong
 * Date: 2022/6/14
 * Desc: 贡献度计算策略类
 */
public abstract class ContributionStrategy {

    protected boolean checkParam(ContributionCalculationParam param){
        if (Objects.isNull(param)){
            return false;
        }
        if (Objects.isNull(param.getCurrentPeriodValue())){
            return false;
        }
        if (Objects.isNull(param.getPreviousPeriodValue())){
            return false;
        }
        if (Objects.isNull(param.getUpperLayerCurrentPeriodValue())){
            return false;
        }
        if (Objects.isNull(param.getUpperLayerPreviousPeriodValue())){
            return false;
        }
        return true;
    }


    public ContributionCalculationResult calculate(ContributionCalculationParam param){
        ContributionCalculationResult result = new ContributionCalculationResult();
        if (!checkParam(param)){
            return result;
        }
        result.setDeltaValue(calculateDeltaValue(param));
        result.setContributionValue(calculateContribution(param));
        result.setDeltaValueRate(calculateDeltaValueRate(param));
        result.setPreviousPeriodValue(param.getPreviousPeriodValue());
        result.setCurrentPeriodValue(param.getCurrentPeriodValue());
        // 计算贡献度占比
        BigDecimal contributionValue = result.getContributionValue();
        BigDecimal upperLayerDeltaValue = calculateUpperLayerDeltaValue(param);
        BigDecimal upperLayerDeltaValueRate = calculateUpperLayerDeltaValueRate(upperLayerDeltaValue,param.getUpperLayerPreviousPeriodValue());
        result.setContributionValueRate(calculateContributionValueRate(contributionValue,upperLayerDeltaValueRate));
        return result;
    }

    protected BigDecimal calculateContributionValueRate(BigDecimal contributionValue, BigDecimal upperLayerDeltaValueRate){
        if (Objects.isNull(contributionValue) || Objects.isNull(upperLayerDeltaValueRate) ||
                upperLayerDeltaValueRate.compareTo(BigDecimal.ZERO) == 0){
            return null;
        } else {
            return contributionValue.divide(upperLayerDeltaValueRate, 10, BigDecimal.ROUND_DOWN);
        }

    }

    protected BigDecimal calculateUpperLayerDeltaValueRate(BigDecimal upperLayerDeltaValue, BigDecimal upperLayerPreviousPeriodValue){
        if (Objects.isNull(upperLayerDeltaValue) || Objects.isNull(upperLayerPreviousPeriodValue) || upperLayerPreviousPeriodValue.doubleValue() == BigDecimal.ZERO.doubleValue()){
            return null;
        } else {
            return upperLayerDeltaValue.divide(upperLayerPreviousPeriodValue, 10, BigDecimal.ROUND_DOWN);
        }

    }


    abstract BigDecimal calculateContribution(ContributionCalculationParam param);

    protected BigDecimal calculateDeltaValue(ContributionCalculationParam param) {
        BigDecimal previousPeriodValue = param.getPreviousPeriodValue();
        BigDecimal currentPeriodValue = param.getCurrentPeriodValue();

        return currentPeriodValue.subtract(previousPeriodValue);
    }

    protected BigDecimal calculateDeltaValueRate(ContributionCalculationParam param) {
        BigDecimal previousPeriodValue = param.getPreviousPeriodValue();
        BigDecimal currentPeriodValue = param.getCurrentPeriodValue();
        BigDecimal subtract = currentPeriodValue.subtract(previousPeriodValue);
        if (param.isRatio()){
            return subtract.setScale(10,BigDecimal.ROUND_DOWN);
        }

        if (previousPeriodValue.compareTo(BigDecimal.ZERO) == 0){
            return null;
        }
        return subtract.divide(previousPeriodValue,10,BigDecimal.ROUND_DOWN);
    }
    protected BigDecimal calculateUpperLayerDeltaValue(ContributionCalculationParam param) {
        BigDecimal previousPeriodValue = param.getUpperLayerPreviousPeriodValue();
        BigDecimal currentPeriodValue = param.getUpperLayerCurrentPeriodValue();

        return currentPeriodValue.subtract(previousPeriodValue);
    }


}
