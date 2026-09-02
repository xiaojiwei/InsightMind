package com.graphinsight.indicator.cache;

import com.alibaba.fastjson.JSON;
import com.graphinsight.indicator.auto.entity.MeasureApplication;
import com.graphinsight.indicator.auto.mapper.MeasureApplicationMapper;
import com.graphinsight.indicator.constant.CacheConstant;
import com.graphinsight.indicator.manager.CacheManager;
import com.graphinsight.indicator.model.cache.MeasureDependencyTreeInfo;
import com.graphinsight.indicator.model.cache.MetadataCache;
import com.graphinsight.indicator.service.RedisCacheService;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.junit4.SpringRunner;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Date: 2022/1/24
 * Desc:
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@RunWith(SpringRunner.class)
@ActiveProfiles("dev")
public class CacheManagerTest {

    @Autowired
    RedisCacheService redisCacheService;
    @Autowired
    CacheManager cacheManager;
    @Autowired
    MeasureApplicationMapper measureApplicationMapper;

    @Test
    public void testRedisIncr(){
        for (int i = 0; i < 10; i++) {
            System.out.print("自增前:" + redisCacheService.getLong(CacheConstant.CACHE_VERSION_KEY) + "----->");
            redisCacheService.increment(CacheConstant.CACHE_VERSION_KEY);
            Long aLong = redisCacheService.getLong(CacheConstant.CACHE_VERSION_KEY);
            System.out.println("自增后" + aLong);
        }
    }

    @Test
    public void testInit(){
        cacheManager.reloadCache();
    }

    @Test
    public void testReadCache(){
        MetadataCache metadataCache = cacheManager.getMetadataCache();
        System.out.println(metadataCache);
    }

    @Test
    public void testMeasureDependencyBuild(){
        List<MeasureApplication> measureApplications = measureApplicationMapper.selectList(null);
        Map<Integer, List<MeasureApplication>> measIdAppList = measureApplications.stream().collect(Collectors.groupingBy(MeasureApplication::getMeasId));
        List<MeasureDependencyTreeInfo> complexMeasureCaches = cacheManager.buildDependencyTree(measIdAppList,null);
        System.out.println(JSON.toJSONString(complexMeasureCaches));
    }
}
