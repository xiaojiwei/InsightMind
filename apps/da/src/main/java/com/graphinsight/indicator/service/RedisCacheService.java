package com.graphinsight.indicator.service;

import com.alibaba.fastjson.JSONObject;

import java.util.List;
import java.util.concurrent.TimeUnit;

public interface RedisCacheService {

    <T> List<T> rangeAll(String key, Class<T> clazz);

    <T> void lpush(String key, T value);

    /**
     * 添加缓存
     */
    <T> void put(String key, T value);

    <T> void put(String key, T value, long timeout, TimeUnit timeUnit);

    <T> void put(String key, T value, int seconds);

    /**
     * 删除缓存
     */
    boolean delete(String key);

    /**
     * 查询缓存
     */
    <T> T get(String key, Class<T> clazz);

    void increment(String key);

    Long getLong(String key);

    boolean setIfAbsent(String key, Object value);

    boolean tryLock(String key, String value, long timeout, TimeUnit timeUnit);

    boolean releaseLock(String key, String targetValue);

    boolean lpush(String queueName, String value);

    String rpop(String queueName);

    void permanentPut(String key, Object value);

    JSONObject getJSONObject(String key);

    boolean hasKey(String key);

    /**
     * 模式匹配删除
     *
     * @param pattern 例：your_pattern_*
     * @return boolean
     */
    Long deleteByPattern(String pattern);
}
