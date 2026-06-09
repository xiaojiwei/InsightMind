package com.graphinsight.indicator.model.vo;

import java.util.List;

import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

@Data
public class AttributionAnalysisDimensionResponseVO {
    @ApiModelProperty(value = "维度序号")
    private int order;
    @ApiModelProperty(value = "维度编码")
    private String dimensionCode;
    @ApiModelProperty(value = "维度名称")
    private String dimensionName;
    @ApiModelProperty(value = "相同趋势")
    private List<DimensionValueTrendResponseVO> sameTrend;
    @ApiModelProperty(value = "相反趋势")
    private List<DimensionValueTrendResponseVO> oppositeTrend;
}
