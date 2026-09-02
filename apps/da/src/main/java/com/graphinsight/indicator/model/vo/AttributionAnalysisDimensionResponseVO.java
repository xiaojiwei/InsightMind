package com.graphinsight.indicator.model.vo;

import java.util.List;

import lombok.Data;

@Data
public class AttributionAnalysisDimensionResponseVO {
    private int order;
    private String dimensionCode;
    private String dimensionName;
    private List<DimensionValueTrendResponseVO> sameTrend;
    private List<DimensionValueTrendResponseVO> oppositeTrend;
}
