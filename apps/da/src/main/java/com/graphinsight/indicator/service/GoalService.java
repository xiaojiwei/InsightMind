package com.graphinsight.indicator.service;

import com.graphinsight.indicator.auto.entity.Goal;
import com.graphinsight.indicator.exception.GoalNotUniqueException;
import com.graphinsight.indicator.exception.QueryRealNumForGoalException;
import com.graphinsight.indicator.model.DataSource;
import com.graphinsight.indicator.model.dto.GoalDTO;
import com.graphinsight.indicator.model.dto.GoalDateDimDTO;
import com.graphinsight.indicator.model.dto.GoalMeasureBaseInfo;
import com.graphinsight.indicator.model.vo.BaseInfo;
import com.graphinsight.indicator.model.vo.GoalAddVO;
import com.graphinsight.indicator.model.vo.GoalQueryVO;

import java.math.BigDecimal;
import java.util.List;

public interface GoalService {


    List<GoalDateDimDTO> getDateDim(Integer spaceId, String measureCode);

    List<GoalMeasureBaseInfo> getMeasure(Integer spaceId, Integer dimViewType, String dimensionValue);

    GoalDTO update(GoalDTO goalDTO) throws Exception;

    List<GoalDTO> add(GoalAddVO goalAddVo) throws GoalNotUniqueException;

    boolean delete(Long goal);

    List<GoalDTO> query(GoalQueryVO goalQueryVO);

    List<GoalDTO> list(Integer spaceId);

    GoalDTO detail(Long goalId);

    List<BaseInfo> getDimForSubGoal(Integer goalId);

    String getGoalName(Goal goal);
}
