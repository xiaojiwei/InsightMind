package com.graphinsight.indicator.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.graphinsight.indicator.auto.entity.Goal;
import com.graphinsight.indicator.auto.entity.TSpace;
import com.graphinsight.indicator.auto.mapper.GoalMapper;
import com.graphinsight.indicator.auto.mapper.TSpaceMapper;
import com.graphinsight.indicator.enums.ResourceEnum;
import com.graphinsight.indicator.model.dto.IndicatorBean;
import com.graphinsight.indicator.model.dto.RelatedResourceDTO;
import com.graphinsight.indicator.service.GoalService;
import com.graphinsight.indicator.service.ReferenceService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.LinkedList;
import java.util.List;

@Service
public class GoalReferenceServiceImpl implements ReferenceService {

    @Autowired
    GoalService goalService;

    @Autowired
    GoalMapper goalMapper;

    @Autowired
    TSpaceMapper tSpaceMapper;

    @Override
    public List<RelatedResourceDTO> listRelatedResource(IndicatorBean bean) {
        List<RelatedResourceDTO> res = new LinkedList<>();
        if (bean == null) return res;
        String code = bean.getCode();
        List<Goal> goals = bean.getCode().startsWith("MEAS") ?
                goalMapper.selectList(Wrappers.<Goal>lambdaQuery().eq(Goal::getDimensionCode, code)) :
                goalMapper.selectList(Wrappers.<Goal>lambdaQuery().eq(Goal::getMeasureCode, code).isNull(Goal::getParentId));
        for (Goal goal : goals) {
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
        return res;
    }

    private String getSpaceName(Long id) {
        TSpace tSpace = tSpaceMapper.selectById(id);
        if (tSpace == null) return null;
        return tSpace.getName();
    }
}
