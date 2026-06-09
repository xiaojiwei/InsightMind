package com.graphinsight.indicator.init;

import com.graphinsight.indicator.auto.entity.DimensionAnalysisTask;
import com.graphinsight.indicator.auto.service.IDimensionAnalysisTaskService;
import com.graphinsight.indicator.enums.DimensionAnalysisTaskStatusType;
import com.graphinsight.indicator.manager.DimensionAnalysisManager;
import com.graphinsight.indicator.service.RedisCacheService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.stereotype.Component;

/**
 * Author: lixiaolong
 * Date: 2022/7/12
 * Desc:
 */
@Component
@Slf4j
public class DimensionAnalysisTaskExecute implements ApplicationListener {

    @Autowired
    private RedisCacheService redisCacheService;
    @Autowired
    private DimensionAnalysisManager manager;
    @Autowired
    private IDimensionAnalysisTaskService taskService;

    public void execute() {
        // while (true) {
        //     String taskId = redisCacheService.rpop(IndicatorConstant.DIMENSION_ANALYSIS_TASK_QUEUE);
        //     DimensionAnalysisTask analysisTask = null;
        //     if (Objects.nonNull(taskId)) {
        //         try {
        //             analysisTask = taskService.getById(Long.valueOf(taskId));
        //             if (analysisTask == null) {
        //                 // 任务不存在或者状态不是初始化，跳过处理
        //                 continue;
        //             }
        //             analysisTask.setStatus(DimensionAnalysisTaskStatusType.PROCESSION.getCode());
        //             // TODO 打点
        //             manager.setProgress(analysisTask.getId(), 5);
        //             taskService.updateById(analysisTask);
        //             manager.executeTask(analysisTask);
        //         } catch (Exception e) {
        //             log.error("多维分析任务执行失败,task:{}", analysisTask);
        //             processFail(analysisTask, e);
        //         }
        //     }
        //     try {
        //         // 睡眠0.5秒 避免CPU打满
        //         Thread.sleep(5000L);
        //     } catch (InterruptedException e) {
        //     }
        // }
    }

    private void processFail(DimensionAnalysisTask analysisTask, Exception e) {
        if (analysisTask != null) {
            analysisTask.setStatus(DimensionAnalysisTaskStatusType.FAILED.getCode());
            analysisTask.setErrorMessage(e.getMessage());
            taskService.updateById(analysisTask);
        }
    }

    @Override
    public void onApplicationEvent(ApplicationEvent event) {
        // CompletableFuture.runAsync(() -> {
        //     execute();
        // });
    }
}
