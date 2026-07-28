package com.graphinsight.indicator.util.contribution;

import com.graphinsight.indicator.util.contribution.bean.ContributionCalculationParam;

import java.math.BigDecimal;

/**
 * Date: 2022/6/14
 * Desc:
 */
public class SubtractionStrategy extends ContributionStrategy  {
    /**
     * 减法拆解时加法拆解的变形
     * 比如 利润 = 收入 - 成本
     * C = A - B
     * 等价于
     * C = A + (-B)
     * deltaB = (-B1) - (-B0) 去掉括号变成：B0 - B1
     * deltaA = A1 - A0
     * 也就是说被减数 用当期 - 上期
     * 减数用 上期减 - 基期
     * @param param
     * @return
     */
    @Override
    public BigDecimal calculateContribution(ContributionCalculationParam param) {
        BigDecimal deltaValue = calculateDeltaValue(param);
        if (! param.isMinuend()){
            // 如果不是被减数，需要做正负值转换
            deltaValue = BigDecimal.ZERO.subtract(deltaValue);
        }
        if (BigDecimal.ZERO.compareTo(param.getUpperLayerPreviousPeriodValue()) == 0){
            return null;
        }
        if (param.isParentPatio()){
            return deltaValue;
        }
        return  deltaValue.divide(param.getUpperLayerPreviousPeriodValue(),10,BigDecimal.ROUND_DOWN);
    }
}
