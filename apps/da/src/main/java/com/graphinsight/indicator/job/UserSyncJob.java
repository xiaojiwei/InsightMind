package com.graphinsight.indicator.job;

import com.graphinsight.indicator.constant.IndicatorConstant;
import com.graphinsight.indicator.manager.UserManager;
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
public class UserSyncJob{

    @Autowired
    private UserManager userManager;
    @Autowired
    RedisCacheService redisCacheService;


    @Scheduled(cron = "0 0 2 * * ?")
    public void execute() {
        try {
            String value = UUID.randomUUID().toString();
            if (redisCacheService.tryLock(IndicatorConstant.SYNC_USER_LOCK_KEY,value,3, TimeUnit.HOURS)){
                log.info("开始同步用户信息");
                userManager.syncUser();
                log.info("用户信息同步完成");
                //释放锁
                boolean lock = redisCacheService.releaseLock(IndicatorConstant.SYNC_DEPT_LOCK_KEY, value);
                if (!lock){
                    log.error("删锁失败，请及时处理,key:{}",IndicatorConstant.SYNC_DEPT_LOCK_KEY);
                }
            } else {
              log.info("已有其他进程同步，本进程跳过处理");
            }
        } catch (Exception e) {
            log.error("UserSyncJob 执行异常:",e);
        }
    }
}
