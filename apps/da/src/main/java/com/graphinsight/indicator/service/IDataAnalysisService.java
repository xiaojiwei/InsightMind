package com.graphinsight.indicator.service;

import com.graphinsight.indicator.model.vo.AttributionAnalysisRequestVO;
import com.graphinsight.indicator.model.vo.AttributionAnalysisResponseVO;

public interface IDataAnalysisService {
    public AttributionAnalysisResponseVO attributionOverview(AttributionAnalysisRequestVO attributionAnalysis);

    public AttributionAnalysisResponseVO attributionDetail(AttributionAnalysisRequestVO attributionAnalysis);
}
