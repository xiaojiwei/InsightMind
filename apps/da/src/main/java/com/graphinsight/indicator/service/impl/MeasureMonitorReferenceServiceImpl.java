package com.graphinsight.indicator.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.graphinsight.indicator.auto.entity.*;
import com.graphinsight.indicator.auto.mapper.*;
import com.graphinsight.indicator.dao.FilterDao;
import com.graphinsight.indicator.model.Filter;
import com.graphinsight.indicator.model.dto.IndicatorBean;
import com.graphinsight.indicator.model.dto.RelatedResourceDTO;
import com.graphinsight.indicator.service.ReferenceService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Author: lixiaolong
 * Date: 2023/8/3
 * Desc:
 */
@Service
public class MeasureMonitorReferenceServiceImpl implements ReferenceService {

    @Autowired
    MeasureMonitorMapper measureMonitorMapper;

    @Autowired
    MeasureMonitorRuleMapper ruleMapper;

    @Autowired
    MeasureMonitorRuleDetailMapper ruleDetailMapper;

    @Autowired
    MeasureMonitorDimGroupMapper measureMonitorDimGroupMapper;

    @Autowired
    FilterDao filterDao;

    @Autowired
    TSpaceMapper tSpaceMapper;

    @Autowired
    MeasureMonitorRuleFilterMapper measureMonitorRuleFilterMapper;

    @Override
    public List<RelatedResourceDTO> listRelatedResource(IndicatorBean bean) {
        List<RelatedResourceDTO> resourceDTOS = new ArrayList<>();
        if (bean == null) return resourceDTOS;
        String code = bean.getCode();
        List<MeasureMonitorRuleDetail> ruleDetails;
        if (code.startsWith("MEAS")) {
            ruleDetails = ruleDetailMapper.selectList(Wrappers.<MeasureMonitorRuleDetail>lambdaQuery().eq(MeasureMonitorRuleDetail::getMeasCode, code));
        } else {
            //时间维度
            ruleDetails = ruleDetailMapper.selectList(Wrappers.<MeasureMonitorRuleDetail>lambdaQuery().eq(MeasureMonitorRuleDetail::getDimCode, code));
            //维度分组
            List<MeasureMonitorDimGroup> dimGroups = measureMonitorDimGroupMapper.selectList(Wrappers.<MeasureMonitorDimGroup>lambdaQuery().eq(MeasureMonitorDimGroup::getDimensionCode, code));
            Set<Long> ruleDetailIds = dimGroups.stream().map(MeasureMonitorDimGroup::getRuleDetailId).collect(Collectors.toSet());
            //过滤器
            List<MeasureMonitorRuleFilter> ruleFilters = measureMonitorRuleFilterMapper.selectList(Wrappers.<MeasureMonitorRuleFilter>lambdaQuery().isNotNull(MeasureMonitorRuleFilter::getFilterId));
            ruleFilters.forEach(e -> {
                Optional<Filter> optionalFilter = filterDao.findById(e.getFilterId());
                if (optionalFilter.isPresent()) {
                    Filter filter = optionalFilter.get();
                    String filterCode = filter.getCode();
                    if (filterCode.equals(code)) {
                        ruleDetailIds.add(e.getRuleDetailId());
                    }
                }
            });
            List<MeasureMonitorRuleDetail> dimGroupRuleDetails = new LinkedList<>();
            if (ruleDetailIds.size() != 0) {
                dimGroupRuleDetails = ruleDetailMapper.selectBatchIds(ruleDetailIds);
            }
            ruleDetails.addAll(dimGroupRuleDetails);
        }
        if (ruleDetails.size() == 0) {
            return resourceDTOS;
        }
        List<MeasureMonitor> monitors = getMonitorByRuleDetails(ruleDetails);
        resourceDTOS = monitors.stream().map(this::convert).collect(Collectors.toList());
        return resourceDTOS;
    }

    private RelatedResourceDTO convert(MeasureMonitor measureMonitor) {
        RelatedResourceDTO res = new RelatedResourceDTO();
        res.setType(4);
        res.setTypeName("指标预警");
        res.setName(measureMonitor.getName());
        res.setResourceId(measureMonitor.getId());
        Long spaceId = measureMonitor.getSpaceId();
        res.setSpaceName(getSpaceName(spaceId));
        res.setSpaceId(spaceId);
        res.setCreator(measureMonitor.getCreator());
        res.setCreateDate(measureMonitor.getCreateTime());
        return res;
    }

    private List<MeasureMonitor> getMonitorByRuleDetails(List<MeasureMonitorRuleDetail> ruleDetails) {
        Set<Long> ruleIds = ruleDetails.stream().map(MeasureMonitorRuleDetail::getRuleId).collect(Collectors.toSet());
        List<MeasureMonitorRule> rules = ruleMapper.selectBatchIds(ruleIds);
        Set<Long> monitorIds = rules.stream().map(MeasureMonitorRule::getMonitorId).collect(Collectors.toSet());
        List<MeasureMonitor> monitors;
        monitors = measureMonitorMapper.selectBatchIds(monitorIds);
        return monitors;
    }

    private String getSpaceName(Long id) {
        TSpace tSpace = tSpaceMapper.selectById(id);
        if (tSpace == null) return null;
        return tSpace.getName();
    }
}
