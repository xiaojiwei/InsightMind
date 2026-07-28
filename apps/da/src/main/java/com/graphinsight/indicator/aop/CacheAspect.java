package com.graphinsight.indicator.aop;

import com.graphinsight.indicator.annotation.CheckCacheVersion;
import com.graphinsight.indicator.annotation.ReloadCache;
import com.graphinsight.indicator.annotation.SyncReloadCache;
import com.graphinsight.indicator.manager.CacheManager;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.annotation.After;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.concurrent.CompletableFuture;

/**
 * @Description: 缓存切面
 * @Date: 2021/11/23
 */
@Component
@Aspect
@Slf4j
public class CacheAspect {

    @Autowired
    CacheManager cacheManager;

    @After("@annotation(reloadCache)")
    public void reloadCache(ReloadCache reloadCache) {
        cacheManager.reloadCache();
    }

    @Before("@annotation(checkCacheVersion)")
    public void checkCacheVersion(CheckCacheVersion checkCacheVersion){

        if (cacheManager.isExpired()){
            log.info("缓存已过期,异步重置缓存");
            CompletableFuture.runAsync(() -> {
                cacheManager.refreshCache();
            });
            // cacheManager.refreshCache();
        }
    }

    @After("@annotation(syncReloadCache)")
    public void syncReloadCache(SyncReloadCache syncReloadCache) {
        cacheManager.syncReloadCache();
    }

}
