package com.graphinsight.indicator.controller;

import com.alibaba.fastjson.JSONObject;
import com.graphinsight.indicator.auto.mapper.DimensionMapper;
import com.graphinsight.indicator.auto.mapper.MeasureMapper;
import com.graphinsight.indicator.doris.mapper.DimWithoutTableMapper;
import com.graphinsight.indicator.manager.CacheManager;
import com.graphinsight.indicator.model.Response;
import com.graphinsight.indicator.service.RedisCacheService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * @Description:
 * @Date: 2021/12/17
 */
@RestController
@RequestMapping("/cache")
public class CacheController {

    @Autowired
    CacheManager cacheManager;
    @Autowired
    RedisCacheService redisCacheService;
    @Autowired
    RedisTemplate redisTemplate;
    @Autowired
    DimensionMapper dimensionMapper;
    @Autowired
    MeasureMapper measureMapper;
    @Autowired
    DimWithoutTableMapper dimWithoutTableMapper;

    @GetMapping("/get/from/redis")
    public JSONObject getFromRedis() {
        return cacheManager.getFormRedis();
    }

    @GetMapping("/test/dim")
    public Response testDimWithoutTable() {
        return Response.ok(dimWithoutTableMapper.selectList(null));
    }

    @GetMapping("/del/dim/{id}")
    public Response delDimById(@PathVariable Integer id) {
        int i = dimensionMapper.deleteById(id);
        return Response.ok(i);
    }

    @GetMapping("/del/meas/{id}")
    public Response delMeasById(@PathVariable Integer id) {
        int i = measureMapper.deleteById(id);
        return Response.ok(i);
    }


    @GetMapping("/reloadCache")
    public Response reloadCache() {
        cacheManager.reloadCache();
        return Response.ok();
    }

    @GetMapping("/syncReloadCache")
    public Response syncReloadCache() {
        cacheManager.syncReloadCache();
        return Response.ok();
    }

    @GetMapping("/redis/rangeAll/{key}")
    public Response rangeAll(@PathVariable String key) {
        List<String> rangeAll = redisCacheService.rangeAll(key, String.class);
        return Response.ok(rangeAll);
    }

    @GetMapping("/set/{key}/{value}")
    public Response setKey(@PathVariable String key, @PathVariable String value) {
        redisCacheService.put(key, value);
        return Response.ok();
    }

    @GetMapping("/get/{key}")
    public Response get(@PathVariable String key) {
        Object s = redisCacheService.get(key, Object.class);
        return Response.ok(s);
    }

    @GetMapping("/del/{key}")
    public Response del(@PathVariable String key) {
        boolean delete = redisCacheService.delete(key);
        return Response.ok(delete);
    }

    @GetMapping("/del/pattern/{pattern}")
    public Response deleteByPattern(@PathVariable String pattern) {
        Long effects = redisCacheService.deleteByPattern(pattern);
        return Response.ok(effects);
    }

    @GetMapping("/ping")
    public Object ping() {
        Object execute = redisTemplate.execute(new RedisCallback<String>() {
            @Override
            public String doInRedis(RedisConnection connection) throws DataAccessException {
                return connection.ping();
            }
        });
        return execute;
    }
}
