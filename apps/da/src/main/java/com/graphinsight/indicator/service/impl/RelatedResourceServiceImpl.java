package com.graphinsight.indicator.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.graphinsight.indicator.auto.entity.*;
import com.graphinsight.indicator.auto.mapper.*;
import com.graphinsight.indicator.enums.ResourceEnum;
import com.graphinsight.indicator.model.dto.RelatedResourceDTO;
import com.graphinsight.indicator.service.GoalService;
import com.graphinsight.indicator.service.RelatedResourceService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.*;

@Service
public class RelatedResourceServiceImpl implements RelatedResourceService {

    @Autowired
    BaseConfigureMapper baseConfigureMapper;
    @Autowired
    GoalMapper goalMapper;

    @Autowired
    MeasureMonitorMapper measureMonitorMapper;

    @Autowired
    MeasureMonitorRuleMapper measureMonitorRuleMapper;

    @Autowired
    MeasureMonitorRuleDetailMapper measureMonitorRuleDetailMapper;

    @Autowired
    DataSourceMapper dataSourceMapper;

    @Autowired
    TSpaceMapper tSpaceMapper;

    @Autowired
    WidgetMapper widgetMapper;

    @Autowired
    DashboardMapper dashboardMapper;

    @Autowired
    WidgetDetailMapper widgetDetailMapper;

    @Autowired
    DismantlingTreeMapper dismantlingTreeMapper;

    @Autowired
    DismantlingTreeQuoteMapper dismantlingTreeQuoteMapper;

    @Autowired
    DimensionAnalysisTaskMapper dimensionAnalysisTaskMapper;

    @Autowired
    GoalService goalService;

    private String getSpaceName(Long id){
        TSpace tSpace = tSpaceMapper.selectById(id);
        if (tSpace==null) return null;
        return tSpace.getName();
    }


    @Override
    public List<RelatedResourceDTO> getRelatedResource(String code, Boolean isDim) {
        List<RelatedResourceDTO> res = new LinkedList();
        //数据集
        List<BaseConfigure> baseConfigures = baseConfigureMapper.selectList(Wrappers.<BaseConfigure>lambdaQuery().eq(BaseConfigure::getCode,code));
        for (BaseConfigure baseConfigure:baseConfigures){
            if (baseConfigure.getDataSourceId()!=null){
                DataSource dataSource = dataSourceMapper.selectById(baseConfigure.getDataSourceId());
                if (dataSource!=null){
                    res.add(new RelatedResourceDTO(
                            null,
                            ResourceEnum.DATA_SOURCE.getType(),
                            ResourceEnum.DATA_SOURCE.getName(),
                            dataSource.getName(),
                            dataSource.getId(),
                            dataSource.getSpaceId(),
                            getSpaceName(dataSource.getSpaceId()),
                            dataSource.getCreator(),
                            dataSource.getCreateDate()
                    ));
                }
            }
        }

        //目标管理
        List<Goal> goals = isDim?
                goalMapper.selectList(Wrappers.<Goal>lambdaQuery().eq(Goal::getDimensionCode,code)):
                goalMapper.selectList(Wrappers.<Goal>lambdaQuery().eq(Goal::getMeasureCode,code).isNull(Goal::getParentId));
        for (Goal goal:goals){
            res.add(new RelatedResourceDTO(
                    null,
                    ResourceEnum.GOAL_MANAGEMENT.getType(),
                    ResourceEnum.GOAL_MANAGEMENT.getName(),
                    goalService.getGoalName(goal),
                    goal.getId(),
                    goal.getSpaceId(),
                    getSpaceName(goal.getSpaceId()),
                    goal.getCreator(),
                    goal.getCreateTime()
            ));
        }

        //指标预警
        List<MeasureMonitorRuleDetail> measureMonitorRuleDetails = isDim?
                measureMonitorRuleDetailMapper.selectList(Wrappers.<MeasureMonitorRuleDetail>lambdaQuery().eq(MeasureMonitorRuleDetail::getDimCode,code)):
                measureMonitorRuleDetailMapper.selectList(Wrappers.<MeasureMonitorRuleDetail>lambdaQuery().eq(MeasureMonitorRuleDetail::getMeasCode,code));
        List<Long> measureMonitorRuleIds = new LinkedList<>();
        for (MeasureMonitorRuleDetail mmrd:measureMonitorRuleDetails){
            if (mmrd.getRuleId()!=null) measureMonitorRuleIds.add(mmrd.getRuleId());
        }
        if(!measureMonitorRuleIds.isEmpty()){
            List<MeasureMonitorRule> measureMonitorRules = measureMonitorRuleMapper.selectBatchIds(measureMonitorRuleIds);
            for (MeasureMonitorRule measureMonitorRule:measureMonitorRules){
                MeasureMonitor measureMonitor = measureMonitorMapper.selectById(measureMonitorRule.getMonitorId());
                res.add(new RelatedResourceDTO(
                        null,
                        ResourceEnum.MEASURE_MONITORING.getType(),
                        ResourceEnum.MEASURE_MONITORING.getName(),
                        measureMonitor.getName(),
                        measureMonitor.getId(),
                        measureMonitor.getSpaceId(),
                        getSpaceName(measureMonitor.getSpaceId()),
                        measureMonitor.getCreator(),
                        measureMonitor.getCreateTime()
                ));
            }
        }

        //数据看板
        List<Long> dashboardIds = new LinkedList<>();
        List<WidgetDetail> widgetDetails = widgetDetailMapper.selectList(Wrappers.<WidgetDetail>lambdaQuery().eq(WidgetDetail::getCode,code));
        for (WidgetDetail widgetDetail:widgetDetails){
            Widget widget = widgetMapper.selectById(widgetDetail.getWidgetId());
            dashboardIds.add(widget.getDashboardId());
        }
        if (!dashboardIds.isEmpty()){
            List<Dashboard> dashboards = dashboardMapper.selectBatchIds(dashboardIds);
            for (Dashboard dashboard:dashboards){
                if (dashboard.getIsDelete()!=1){
                    res.add(new RelatedResourceDTO(
                            null,
                            ResourceEnum.DASHBOARD.getType(),
                            ResourceEnum.DASHBOARD.getName(),
                            dashboard.getName(),
                            dashboard.getId(),
                            dashboard.getSpaceId(),
                            getSpaceName(dashboard.getSpaceId()),
                            dashboard.getCreator(),
                            dashboard.getCreateTime()
                    ));
                }
            }
        }

        //拆解树
        List<Long> treeIds = new LinkedList<>();
        List<DismantlingTreeQuote> dismantlingTreeQuotes = dismantlingTreeQuoteMapper.selectList(Wrappers.<DismantlingTreeQuote>lambdaQuery().eq(DismantlingTreeQuote::getCode,code));
        for (DismantlingTreeQuote dismantlingTreeQuote:dismantlingTreeQuotes){
            treeIds.add(dismantlingTreeQuote.getTreeId());
        }
        if(!treeIds.isEmpty()){
            List<DismantlingTree> dismantlingTrees = dismantlingTreeMapper.selectBatchIds(treeIds);
            for (DismantlingTree dismantlingTree:dismantlingTrees){
                res.add(new RelatedResourceDTO(
                    null,
                    ResourceEnum.DISMANTLING_TREE.getType(),
                    ResourceEnum.DISMANTLING_TREE.getName(),
                    dismantlingTree.getName(),
                    dismantlingTree.getId(),
                    dismantlingTree.getSpaceId(),
                    getSpaceName(dismantlingTree.getSpaceId()),
                    dismantlingTree.getCreator(),
                    dismantlingTree.getCreateTime()
                ));
            }
        }

        //多维分析
        List<DimensionAnalysisTask> dimensionAnalysisTasks = isDim?
                dimensionAnalysisTaskMapper.selectList(Wrappers.<DimensionAnalysisTask>lambdaQuery().eq(DimensionAnalysisTask::getDimCode,code)):
                dimensionAnalysisTaskMapper.selectList(Wrappers.<DimensionAnalysisTask>lambdaQuery().eq(DimensionAnalysisTask::getMeasCode,code));
        for (DimensionAnalysisTask dimensionAnalysisTask:dimensionAnalysisTasks){
            res.add(new RelatedResourceDTO(
                null,
                ResourceEnum.DIMENSION_ANALYSIS_TASK.getType(),
                ResourceEnum.DIMENSION_ANALYSIS_TASK.getName(),
                dimensionAnalysisTask.getReportName(),
                dimensionAnalysisTask.getId(),
                dimensionAnalysisTask.getSpaceId(),
                getSpaceName(dimensionAnalysisTask.getSpaceId()),
                dimensionAnalysisTask.getCreator(),
                dimensionAnalysisTask.getCreateTime()
            ));
        }

        //按照资源创建时间降序排列，并生成序号
        Collections.sort(res, new Comparator<RelatedResourceDTO>() {
            @Override
            public int compare(RelatedResourceDTO o1, RelatedResourceDTO o2) {
                return o2.getCreateDate().compareTo(o1.getCreateDate());
            }
        });
        int index = 1;
        Iterator<RelatedResourceDTO> iterator = res.iterator();
        while (iterator.hasNext()){
            RelatedResourceDTO ele = iterator.next();
            ele.setId(index++);
        }
        return res;
    }



}
