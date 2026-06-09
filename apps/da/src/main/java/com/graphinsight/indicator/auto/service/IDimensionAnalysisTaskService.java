package com.graphinsight.indicator.auto.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.graphinsight.indicator.auto.entity.DimensionAnalysisTask;

import java.util.List;

/**
 * <p>
 * 多维分析查询任务列表 服务类
 * </p>
 *
 * @author lixiaolong5
 * @since 2022-07-05
 */
public interface IDimensionAnalysisTaskService extends IService<DimensionAnalysisTask> {
    List<DimensionAnalysisTask> getByMeasCode(String measCode);
}
