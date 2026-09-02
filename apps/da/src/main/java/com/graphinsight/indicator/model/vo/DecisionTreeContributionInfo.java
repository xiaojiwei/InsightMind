package com.graphinsight.indicator.model.vo;

import lombok.Data;

/**
 * Date: 2022/6/21
 * Desc:
 */
@Data
public class DecisionTreeContributionInfo {

    /**
     * 波动值
     */
    private String deltaValue;

    /**
     * 波动比率
     */
    private String deltaValueRate;

    /**
     * 对上层指标贡献度
     */
    private String contributionValue;


    /**
     * 上期的值
     */
    private String previousPeriodValue;

    /**
     * 本期的值
     */
    private String currentPeriodValue;


    private DimensionAnalysisDetailVO dimensionAnalysisDetail;


}
