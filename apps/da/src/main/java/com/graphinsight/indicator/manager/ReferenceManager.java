package com.graphinsight.indicator.manager;

import com.graphinsight.indicator.model.dto.IndicatorBean;
import com.graphinsight.indicator.model.dto.RelatedResourceDTO;
import com.graphinsight.indicator.service.ReferenceService;
import com.graphinsight.indicator.util.SpringBeanUtil;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Author: lixiaolong
 * Date: 2023/8/3
 * Desc:
 */
@Service
public class ReferenceManager {

    public List<RelatedResourceDTO> listRelatedResource(IndicatorBean bean){
        Map<String, ReferenceService> referenceServiceMap = SpringBeanUtil.getBeansOfType(ReferenceService.class);
        List<RelatedResourceDTO> relatedResourceDTOS = new ArrayList<>();
        referenceServiceMap.values().forEach(referenceService -> {
            List<RelatedResourceDTO> dtos = referenceService.listRelatedResource(bean);
            relatedResourceDTOS.addAll(dtos);
        });
        return relatedResourceDTOS;
    }

}
