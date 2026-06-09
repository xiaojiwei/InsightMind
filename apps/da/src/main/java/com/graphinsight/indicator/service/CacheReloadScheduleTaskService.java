package com.graphinsight.indicator.service;

import org.springframework.scheduling.annotation.Async;

import java.util.Set;

/**
 * CahceService
 */
public interface CacheReloadScheduleTaskService {

    /**
     * 刷新cache数据
     */
    void flushCacheData();

    /**
     * 创建CacheTask
     */
    void createCacheTask();

    @Async
    void buildDimData(Set<String> dimNames);

    /**
     * 构建所有维度数据
     */
    void buildAllDimData();

    /**
     * 快照数据
     * @return
     */
    String snapshot();

}
