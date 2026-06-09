package com.graphinsight.indicator.auto.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.graphinsight.indicator.auto.entity.DimensionAnalysisTask;
import com.graphinsight.indicator.auto.mapper.DimensionAnalysisTaskMapper;
import com.graphinsight.indicator.auto.service.IDimensionAnalysisTaskService;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * <p>
 * 多维分析查询任务列表 服务实现类
 * </p>
 *
 * @author lixiaolong5
 * @since 2022-07-05
 */
@Service
public class DimensionAnalysisTaskServiceImpl extends ServiceImpl<DimensionAnalysisTaskMapper, DimensionAnalysisTask> implements IDimensionAnalysisTaskService {

    @Override
    public List<DimensionAnalysisTask> getByMeasCode(String measCode) {
        QueryWrapper<DimensionAnalysisTask> queryWrapper = new QueryWrapper<>();
        queryWrapper.lambda().eq(DimensionAnalysisTask::getMeasCode, measCode);
        return baseMapper.selectList(queryWrapper);
    }
}
