package com.graphinsight.indicator.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.graphinsight.indicator.auto.entity.Dashboard;
import com.graphinsight.indicator.auto.entity.Widget;
import com.graphinsight.indicator.auto.entity.WidgetDetail;
import com.graphinsight.indicator.auto.service.IDashboardService;
import com.graphinsight.indicator.auto.service.IWidgetDetailService;
import com.graphinsight.indicator.auto.service.IWidgetService;
import com.graphinsight.indicator.enums.ResourceEnum;
import com.graphinsight.indicator.enums.YesNoType;
import com.graphinsight.indicator.model.dto.IndicatorBean;
import com.graphinsight.indicator.model.dto.RelatedResourceDTO;
import com.graphinsight.indicator.service.ReferenceService;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;

import javax.annotation.Resource;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Author: lixiaolong
 * Date: 2023/8/3
 * Desc:
 */
@Service
public class DashboardReferenceServiceImpl implements ReferenceService {

    @Resource
    IWidgetDetailService widgetDetailService;
    @Resource
    IDashboardService dashboardService;
    @Resource
    IWidgetService widgetService;

    @Override
    public List<RelatedResourceDTO> listRelatedResource(IndicatorBean bean) {
        List<RelatedResourceDTO> resourceDTOS = new ArrayList<>();
        if (bean != null) {
            List<WidgetDetail> details = widgetDetailService.list(Wrappers.<WidgetDetail>lambdaQuery().eq(WidgetDetail::getCode, bean.getCode()));
            if (!CollectionUtils.isEmpty(details)) {
                Set<Long> widgetIds = details.stream().map(WidgetDetail::getWidgetId).collect(Collectors.toSet());
                List<Widget> widgets = widgetService.listByIds(widgetIds);
                List<Dashboard> dashboards = dashboardService.list(Wrappers.<Dashboard>lambdaQuery().eq(Dashboard::getIsDelete, YesNoType.NO.getCode()));
                List<Widget> usefulWidgets = removeUselessVersionId(widgets, dashboards);
                Map<Long, Widget> widgetMap = usefulWidgets.stream().collect(Collectors.toMap(Widget::getId, w -> w));
                Map<Long, Dashboard> dashboardMap = dashboards.stream().collect(Collectors.toMap(Dashboard::getId, d -> d));
                List<RelatedResourceDTO> dtos = widgetIds.stream().map(id -> getRelatedResourceDTO(widgetMap.get(id), dashboardMap)).filter(dto -> dto != null).collect(Collectors.toList());
                resourceDTOS.addAll(dtos);
            }
        }
        return resourceDTOS;
    }

    private List<Widget> removeUselessVersionId(List<Widget> widgets, List<Dashboard> dashboards){
        List<Widget> res = new ArrayList();
        Set<Long> dashboardVersionIds = new HashSet<>();
        dashboards.forEach(d -> {
            dashboardVersionIds.add(d.getLatestVersionId());
            dashboardVersionIds.add(d.getOnlineVersionId());
        });

        widgets.forEach(w -> {
            if (dashboardVersionIds.contains(w.getDashboardVersionId())){
                res.add(w);
            }
        });
        return res;
    }

    private RelatedResourceDTO getRelatedResourceDTO(Widget widget, Map<Long, Dashboard> dashboardMap) {
        if (widget == null){
            return null;
        }
        Dashboard dashboard = dashboardMap.get(widget.getDashboardId());
        if (dashboard != null) {
            RelatedResourceDTO dto = new RelatedResourceDTO();
            dto.setResourceId(widget.getDashboardId());
            dto.setName(widget.getName());
            Long spaceId = dashboard.getSpaceId();
            dto.setSpaceId(spaceId);
            dto.setType(ResourceEnum.DASHBOARD.getType());
            dto.setTypeName("数据看板");
            dto.setCreateDate(dashboard.getCreateTime());
            dto.setSpaceId(dashboard.getSpaceId());
            return dto;
        } else {
            return null;
        }
    }
}
