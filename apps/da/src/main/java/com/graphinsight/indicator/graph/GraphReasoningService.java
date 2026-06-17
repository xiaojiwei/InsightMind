package com.graphinsight.indicator.graph;

import java.util.List;

public interface GraphReasoningService {

    List<ReasoningRelationDTO> listCompatibleDimensions(String measureCode);

    List<ReasoningRelationDTO> listUpstreamMeasures(String measureCode);

    List<ReasoningRelationDTO> listDownstreamMeasures(String measureCode);
}
