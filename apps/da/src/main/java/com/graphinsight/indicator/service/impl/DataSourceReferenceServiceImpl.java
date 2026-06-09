package com.graphinsight.indicator.service.impl;

import com.baomidou.mybatisplus.core.toolkit.CollectionUtils;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.graphinsight.indicator.auto.entity.BaseConfigure;
import com.graphinsight.indicator.auto.entity.DataSource;
import com.graphinsight.indicator.auto.service.IBaseConfigureService;
import com.graphinsight.indicator.auto.service.IDataSourceService;
import com.graphinsight.indicator.enums.ResourceEnum;
import com.graphinsight.indicator.model.dto.IndicatorBean;
import com.graphinsight.indicator.model.dto.RelatedResourceDTO;
import com.graphinsight.indicator.service.ReferenceService;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Author: lixiaolong
 * Date: 2023/8/3
 * Desc:
 */
@Service
public class DataSourceReferenceServiceImpl implements ReferenceService {

    @Resource
    IBaseConfigureService baseConfigureService;
    @Resource
    IDataSourceService dataSourceService;

    @Override
    public List<RelatedResourceDTO> listRelatedResource(IndicatorBean bean) {
        List<RelatedResourceDTO> resourceDTOS = new ArrayList<>();

        if (bean != null) {
            //数据集
            List<BaseConfigure> baseConfigures = baseConfigureService.list(Wrappers.<BaseConfigure>lambdaQuery().eq(BaseConfigure::getCode, bean.getCode()));
            Set<Long> dataSourceIds = baseConfigures.stream().map(BaseConfigure::getDataSourceId).filter(id -> Objects.nonNull(id)).collect(Collectors.toSet());
            if (CollectionUtils.isNotEmpty(dataSourceIds)){
                List<RelatedResourceDTO> dtos = dataSourceService.listByIds(dataSourceIds).stream()
                        .map(dataSource -> getRelatedResourceDTO(dataSource))
                        .collect(Collectors.toList());
                resourceDTOS.addAll(dtos);
            }

        }
        return resourceDTOS;
    }

    private RelatedResourceDTO getRelatedResourceDTO(DataSource dataSource) {
        RelatedResourceDTO dto = new RelatedResourceDTO();
        dto.setType(ResourceEnum.DATA_SOURCE.getType());
        dto.setTypeName("数据集");
        dto.setCreateDate(dataSource.getCreateDate());
        dto.setResourceId(dataSource.getId());
        dto.setSpaceId(dataSource.getSpaceId());
        dto.setName(dataSource.getName());
        return dto;
    }
}
