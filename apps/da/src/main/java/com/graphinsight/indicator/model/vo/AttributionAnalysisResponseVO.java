package com.graphinsight.indicator.model.vo;

import java.math.BigDecimal;
import java.util.List;

import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

@Data
public class AttributionAnalysisResponseVO {
    @ApiModelProperty(value = "指标编码")
    private String measureCode;
    @ApiModelProperty(value = "指标名称")
    private String measureName;
    @ApiModelProperty(value = "基期")
    private String basePeriod;
    @ApiModelProperty(value = "本期")
    private String currentPeriod;
    @ApiModelProperty(value = "基期值")
    private BigDecimal baseValue;
    @ApiModelProperty(value = "本期值")
    private BigDecimal currentValue;
    @ApiModelProperty(value = "本期与基期差值，即 本期值 - 基期值")
    private BigDecimal delta;
    @ApiModelProperty(value = "本期与基期差值占比，即 (本期值 - 基期值)/基期值")
    private BigDecimal deltaPercent;
    @ApiModelProperty(value = "趋势：0-持平，1-上升，-1-下降")
    private int trend;
    @ApiModelProperty(value = "当前指标下维度")
    private List<AttributionAnalysisDimensionResponseVO> dimensions;
}