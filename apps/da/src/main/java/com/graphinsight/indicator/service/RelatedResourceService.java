package com.graphinsight.indicator.service;

import com.graphinsight.indicator.model.dto.RelatedResourceDTO;

import java.util.List;

public interface RelatedResourceService {

    List<RelatedResourceDTO> getRelatedResource(String code,Boolean isDim);
}
