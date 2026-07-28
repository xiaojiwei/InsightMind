package com.graphinsight.indicator.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.graphinsight.indicator.auto.entity.DismantlingTree;
import com.graphinsight.indicator.auto.entity.DismantlingTreeQuote;
import com.graphinsight.indicator.auto.service.IDismantlingTreeQuoteService;
import com.graphinsight.indicator.auto.service.IDismantlingTreeService;
import com.graphinsight.indicator.enums.ResourceEnum;
import com.graphinsight.indicator.model.dto.IndicatorBean;
import com.graphinsight.indicator.model.dto.RelatedResourceDTO;
import com.graphinsight.indicator.service.ReferenceService;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;

import javax.annotation.Resource;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Date: 2023/8/3
 * Desc:
 */
@Service
public class DismantlingTreeReferenceServiceImpl implements ReferenceService {

    @Resource
    IDismantlingTreeQuoteService quoteService;
    @Resource
    IDismantlingTreeService dismantlingTreeService;

    @Override
    public List<RelatedResourceDTO> listRelatedResource(IndicatorBean bean) {
        List<RelatedResourceDTO> resourceDTOS = new ArrayList<>();
        if (bean != null) {
            List<DismantlingTreeQuote> quotes = quoteService.list(Wrappers.<DismantlingTreeQuote>lambdaQuery().eq(DismantlingTreeQuote::getCode, bean.getCode()));
            if (!CollectionUtils.isEmpty(quotes)) {
                Set<Long> treeIds = quotes.stream().map(DismantlingTreeQuote::getTreeId).collect(Collectors.toSet());
                List<DismantlingTree> dismantlingTrees = dismantlingTreeService.listByIds(treeIds);
                List<RelatedResourceDTO> dtos = dismantlingTrees.stream().map(tree -> getRelatedResourceDTO(tree)).collect(Collectors.toList());
                resourceDTOS.addAll(dtos);
            }
        }
        return resourceDTOS;
    }

    private RelatedResourceDTO getRelatedResourceDTO(DismantlingTree tree) {
        RelatedResourceDTO dto = new RelatedResourceDTO();
        if (tree != null) {
            dto.setResourceId(tree.getId());
            dto.setName(tree.getName());
            dto.setSpaceId(tree.getSpaceId());
            dto.setType(ResourceEnum.DISMANTLING_TREE.getType());
            dto.setTypeName("拆解树");
            dto.setCreateDate(tree.getCreateTime());
        }
        return dto;
    }
}
