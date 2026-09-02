package com.graphinsight.indicator.model.vo;

import com.graphinsight.indicator.enums.ViewType;

import lombok.Data;

@Data
public class AttributionAnalysisRequestVO {
    private String measureCode;
    private String filterDimCode;
    private String dimensionCode;
    private String basePeriod;
    private String currentPeriod;
    private ViewType viewType;
    private int trend;
}
