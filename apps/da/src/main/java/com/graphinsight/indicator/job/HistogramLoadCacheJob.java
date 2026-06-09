package com.graphinsight.indicator.job;

import com.graphinsight.indicator.manager.HistogramManager;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Author: lixiaolong
 * Date: 2022/3/5
 * Desc:
 */
@Component
@Slf4j
public class HistogramLoadCacheJob {

    @Autowired
    private HistogramManager histogramManager;


    @Scheduled(cron = "0 0 8 * * ?")
    public void execute() {
        try {
           histogramManager.loadCache();
        } catch (Exception e) {
            log.error("HistogramLoadCacheJob 执行异常",e);
        }
    }
}
