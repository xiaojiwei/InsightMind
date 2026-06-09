package com.graphinsight.indicator.service;

import com.graphinsight.indicator.model.dto.IndicatorBean;
import com.graphinsight.indicator.model.dto.RelatedResourceDTO;

import java.util.List;

/**
 * Author: lixiaolong
 * Date: 2023/8/3
 * Desc:
 */
public interface ReferenceService {

    List<RelatedResourceDTO> listRelatedResource(IndicatorBean bean);

}
