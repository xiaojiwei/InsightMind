package com.graphinsight.indicator.job;

import com.alibaba.fastjson.JSON;
import com.graphinsight.indicator.auto.entity.DimensionAnalysisTask;
import com.graphinsight.indicator.auto.service.IDimensionAnalysisTaskService;
import com.graphinsight.indicator.constant.IndicatorConstant;
import com.graphinsight.indicator.enums.DimensionAnalysisTaskStatusType;
import com.graphinsight.indicator.manager.DimensionAnalysisManager;
import com.graphinsight.indicator.service.RedisCacheService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.Objects;
import java.util.concurrent.TimeUnit;

/**
 * Date: 2022/7/12
 * Desc:
 */
@Component
@Slf4j
@ConditionalOnProperty(name = "jobs.dimension-analysis.enabled", havingValue = "true", matchIfMissing = true)
public class DimensionAnalysisTaskExecuteJob {

    @Autowired
    private RedisCacheService redisCacheService;
    @Autowired
    private DimensionAnalysisManager manager;
    @Autowired
    private IDimensionAnalysisTaskService taskService;
    @Value("${redisKeyPrefix}")
    String redisKeyPrefix;
    /**
     * 分布式锁的Key
     */
    private static final String LOCK_KEY = "DimensionAnalysisTaskExecuteJob_Distribution_Lock_Key";
    /**
     * 锁的过期时间，单位为秒
     */
    private static final int LOCK_EXPIRE_TIME_IN_SECONDS = 421;
    /**
     * 分布式锁的Value
     */
    private static final String LOCK_VALUE = "LOCKED";

    @Scheduled(fixedDelay = 9973)
    public void execute() {
        try {
            boolean lockAcquired = redisCacheService.tryLock(redisKeyPrefix + LOCK_KEY, LOCK_VALUE, LOCK_EXPIRE_TIME_IN_SECONDS, TimeUnit.SECONDS);
            if (!lockAcquired) {
                log.info("[多维分析任务],未获取到锁，停止执行");
                return;
            }

            try {
                log.info("[多维分析任务],已获取到锁，开始执行");
                doExecute();
            } finally {
                redisCacheService.releaseLock(redisKeyPrefix + LOCK_KEY, LOCK_VALUE);
            }
        } catch (Exception e) {
            log.error("[多维分析任务],DimensionAnalysisTaskExecuteJob 执行异常:{}", e.getMessage(), e);
        }
    }

    private void doExecute() {
        String taskId = redisCacheService.rpop(redisKeyPrefix + IndicatorConstant.DIMENSION_ANALYSIS_TASK_QUEUE);
        DimensionAnalysisTask analysisTask = null;
        if (Objects.nonNull(taskId)) {
            try {
                log.info("[多维分析任务],查询到任务ID:{},开始处理", taskId);
                analysisTask = taskService.getById(Long.valueOf(taskId));
                if (analysisTask == null) {
                    log.info("[多维分析任务],查询到任务ID:{},任务不存在或者状态不是初始化，跳过处理", taskId);
                    return;
                }
                analysisTask.setStatus(DimensionAnalysisTaskStatusType.PROCESSION.getCode());

                log.info("[多维分析任务],查询到任务ID:{},任务实体:{}", taskId, JSON.toJSONString(analysisTask));

                manager.setProgress(analysisTask.getId(), 5);
                taskService.updateById(analysisTask);
                manager.executeTask(analysisTask);
                log.info("[多维分析任务],查询到任务ID:{},完成处理", taskId);
            } catch (Exception e) {
                log.error("[多维分析任务],执行失败,task:{},message:{}", analysisTask, e.getMessage(), e);
                processFail(analysisTask, e);
            }
        }
    }

    private void processFail(DimensionAnalysisTask analysisTask, Exception e) {
        if (analysisTask != null) {
            analysisTask.setStatus(DimensionAnalysisTaskStatusType.FAILED.getCode());
            analysisTask.setErrorMessage(e.getMessage());
            taskService.updateById(analysisTask);
        }
    }
}
