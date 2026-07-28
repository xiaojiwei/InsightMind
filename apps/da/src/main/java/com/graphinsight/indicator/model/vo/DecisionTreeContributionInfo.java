package com.graphinsight.indicator.model.vo;

import io.swagger.annotations.ApiModelProperty;
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
    @ApiModelProperty(value = "波动值")
    private String deltaValue;

    /**
     * 波动比率
     */
    @ApiModelProperty(value = "波动比率,如果为null，显示 '-' ")
    private String deltaValueRate;

    /**
     * 对上层指标贡献度
     */
    @ApiModelProperty(value = "对上层指标的贡献值,如果为null，显示 '-' ")
    private String contributionValue;


    /**
     * 上期的值
     */
    @ApiModelProperty(value = "上(基)期值")
    private String previousPeriodValue;

    /**
     * 本期的值
     */
    @ApiModelProperty(value = "本期值")
    private String currentPeriodValue;


    @ApiModelProperty(value = "多维分析任务详情")
    private DimensionAnalysisDetailVO dimensionAnalysisDetail;


}
