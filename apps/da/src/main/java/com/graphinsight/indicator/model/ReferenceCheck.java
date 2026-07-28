package com.graphinsight.indicator.model;

import com.graphinsight.indicator.auto.entity.Dimension;
import com.graphinsight.indicator.auto.entity.Measure;
import com.graphinsight.indicator.model.dto.RelatedResourceDTO;
import lombok.Data;

import java.util.List;

/**
 * Date: 2023/8/3
 * Desc:
 */
@Data
public class ReferenceCheck {

    private Measure measure;

    private Dimension dimension;


    private List<RelatedResourceDTO> relatedResourceDTOList;
}
