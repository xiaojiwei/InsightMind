package com.graphinsight.indicator.model.vo;

import java.math.BigDecimal;
import java.util.List;

import lombok.Data;

@Data
public class AttributionAnalysisResponseVO {
    private String measureCode;
    private String measureName;
    private String basePeriod;
    private String currentPeriod;
    private BigDecimal baseValue;
    private BigDecimal currentValue;
    private BigDecimal delta;
    private BigDecimal deltaPercent;
    private int trend;
    private List<AttributionAnalysisDimensionResponseVO> dimensions;
}