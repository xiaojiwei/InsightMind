package com.graphinsight.indicator.model.vo;

import java.math.BigDecimal;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

@Data
public class DimensionValueTrendResponseVO {
    @ApiModelProperty(value = "趋势序号")
    private int order;
    @ApiModelProperty(value = "维值")
    private String dimensionValue;
    @ApiModelProperty(value = "基期值")
    private BigDecimal baseValue;
    @ApiModelProperty(value = "本期值")
    private BigDecimal currentValue;
    @ApiModelProperty(value = "本期与基期差值，即 本期值 - 基期值")
    private BigDecimal delta;
    @ApiModelProperty(value = "本期与基期差值占比，即 (本期值 - 基期值)/基期值")
    private BigDecimal deltaPercent;
}
