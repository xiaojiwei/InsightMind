package com.graphinsight.indicator.job;

import com.graphinsight.indicator.constant.IndicatorConstant;
import com.graphinsight.indicator.manager.HistogramManager;
import com.graphinsight.indicator.service.RedisCacheService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * Author: lixiaolong
 * Date: 2022/3/5
 * Desc:
 */
@Component
@Slf4j
public class HistogramJob {

    @Autowired
    private HistogramManager histogramManager;
    @Autowired
    RedisCacheService redisCacheService;


    @Scheduled(cron = "0 0 5 * * ?")
    public void execute() {
        try {
            String value = UUID.randomUUID().toString();
            if (redisCacheService.tryLock(IndicatorConstant.HISTOGRAM_LOCK_KEY,value,3, TimeUnit.HOURS)){
                log.info("开始执行统计直方图");
                histogramManager.runTable();
                histogramManager.runDimension();
                //释放锁
                boolean lock = redisCacheService.releaseLock(IndicatorConstant.HISTOGRAM_LOCK_KEY, value);
                if (!lock){
                    log.error("删锁失败，请及时处理,key:{}",IndicatorConstant.HISTOGRAM_LOCK_KEY);
                }
            } else {
              log.info("已有其他进程同步，本进程跳过处理");
            }
        } catch (Exception e) {
            log.error("HistogramJob 执行异常",e);
        }
    }
}
