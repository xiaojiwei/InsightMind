package com.graphinsight.indicator.util.gini;

import com.graphinsight.indicator.exception.GiniCalculationException;
import com.graphinsight.indicator.model.dto.GiniCalculateParam;
import com.graphinsight.indicator.model.dto.GiniSubOption;
import com.graphinsight.indicator.util.MathUtil;
import com.graphinsight.indicator.util.gini.bean.GiniCalculateUnit;
import org.springframework.util.CollectionUtils;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Date: 2022/7/11
 * Desc:基尼系数计算器
 */
public class GiniCalculator {

    public static double calculateGini(GiniCalculateParam param) throws GiniCalculationException{
        if (param == null || CollectionUtils.isEmpty(param.getGiniSubOptionList())){
            throw GiniCalculationException.error("计算参数为空,param: " + param);
        }
        List<GiniSubOption> giniSubOptionList = param.getGiniSubOptionList();
        // 计算累计波动值、累计基期值
        double totalAbsBaseValue = 0.0;
        double totalAbsBasCurrentValue = 0.0;
        double deltaTotalAbsValue = 0.0;
        for (GiniSubOption giniSubOption : giniSubOptionList) {
            totalAbsBaseValue += Math.abs(giniSubOption.getBaseValue());
            deltaTotalAbsValue += Math.abs(giniSubOption.getBaseValue() - giniSubOption.getCurrentValue());
            totalAbsBasCurrentValue += Math.abs(giniSubOption.getBaseValue()) +  Math.abs(giniSubOption.getCurrentValue());
        }

        // 转换成计算单元
        double finalDeltaTotalAbsValue = deltaTotalAbsValue;
        double finalTotalBaseValue = totalAbsBaseValue;
        List<GiniCalculateUnit> unitList = new ArrayList<>();
        for (GiniSubOption giniSubOption : giniSubOptionList) {
            GiniCalculateUnit giniCalculateUnit = new GiniCalculateUnit(giniSubOption.getBaseValue(),giniSubOption.getCurrentValue(),finalDeltaTotalAbsValue, finalTotalBaseValue);
            unitList.add(giniCalculateUnit);
        }
        List<GiniCalculateUnit> sortedUnitList = unitList.stream().sorted(Comparator.comparing(GiniCalculateUnit::getSortBy)).collect(Collectors.toList());

        // 计算累计(基期+本期)占比和累计波动占比
        double[] x_arr = new double[sortedUnitList.size()];
        double[] y_arr = new double[sortedUnitList.size()];

        double accumulativeDeltaValue = 0.0;
        double accumulativeBaseCurrentValue = 0.0;

        for (int i = 0; i < sortedUnitList.size(); i++) {
            GiniCalculateUnit giniCalculateUnit = sortedUnitList.get(i);
            double baseAbsValue = giniCalculateUnit.getBaseAbsValue();
            double currentAbsValue = giniCalculateUnit.getCurrentAbsValue();
            double addup =  baseAbsValue + currentAbsValue + accumulativeBaseCurrentValue;
            // 当前基期累计值+当前本期累计值
            accumulativeBaseCurrentValue += baseAbsValue + currentAbsValue ;
            // 当前波动累计值
            accumulativeDeltaValue += giniCalculateUnit.getDeltaAbsValue();
            // 基期本期累计占比
            x_arr[i] = addup / totalAbsBasCurrentValue;
            // 波动累计占比
            y_arr[i] = accumulativeDeltaValue / deltaTotalAbsValue;
        }
        double s2 = MathUtil.trapz(x_arr, y_arr);
        double s1 = 0.5 - s2;
        return s1 / (s1 + s2);
    }
}
