package com.graphinsight.indicator.service.impl;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.alibaba.fastjson.serializer.SerializerFeature;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonParseException;
import com.graphinsight.indicator.service.RedisCacheService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.lang.reflect.Type;
import java.util.*;
import java.util.concurrent.TimeUnit;

@Service
@Slf4j
public class RedisCacheServiceImpl implements RedisCacheService {

    //    @Autowired
//    private JedisPool jedisPool;
    @Autowired
    private RedisTemplate redisTemplate;

    @Override
    public <T> List<T> rangeAll(String key, Class<T> clazz) {
        try {
            List range = redisTemplate.boundListOps(key).range(0, -1);
            return JSON.parseArray(JSON.toJSONString(range), clazz);
        } catch (Exception e) {
            log.error("leftPush异常,key:{}", key, e);
            return null;
        }
    }

    @Override
    public <T> void lpush(String key, T value) {
        try {
            redisTemplate.boundListOps(key).leftPush(value);
        } catch (Exception e) {
            log.error("leftPush异常,key:{}", key, e);
        }
    }

    @Override
    public <T> void put(String key, T value) {
        try {
            redisTemplate.boundValueOps(key).set(JSON.toJSONString(value, SerializerFeature.WriteMapNullValue),  7, TimeUnit.DAYS);
        } catch (Exception e) {
            log.error("设置缓存异常,key:{}", key, e);
        }

    }

    private static Long getTimeOut() {
        // TODO 自动生成的方法存根
        Calendar c = Calendar.getInstance();
        c.set(Calendar.HOUR_OF_DAY, 0);
        c.set(Calendar.MINUTE, 0);
        c.set(Calendar.SECOND, 30);
        c.set(Calendar.MILLISECOND, 0);
        Long today = c.getTimeInMillis() / 1000;
        return today;
    }

    @Override
    public <T> void put(String key, T value, long timout, TimeUnit timeUnit) {
        try {
            redisTemplate.boundValueOps(key).set(JSON.toJSONString(value, SerializerFeature.WriteMapNullValue), timout, timeUnit);
        } catch (Exception e) {
            log.error("设置缓存异常,key:{}", key, e);
        }
    }

    @Override
    public <T> void put(String key, T value, int seconds) {
        try {
            redisTemplate.boundValueOps(key).set(JSON.toJSONString(value, SerializerFeature.WriteMapNullValue), seconds, TimeUnit.SECONDS);
        } catch (Exception e) {
            log.error("设置缓存异常,key:{}", key, e);
        }
    }

    @Override
    public boolean delete(String key) {
        try {
            Boolean result = redisTemplate.delete(key);
            return result;
        } catch (Exception e) {
            log.error("删除缓存异常key:{}", key, e);
            return false;
        }
    }

    @Override
    public <T> T get(String key, Class<T> clazz) {
        try {
            Object o = redisTemplate.boundValueOps(key).get();
            if (Objects.isNull(o)) {
                return null;
            }
//            T t = JSON.parseObject(o.toString(), clazz);

            // Register an adapter to manage the date types as long values
            GsonBuilder builder = new GsonBuilder();

            // Register an adapter to manage the date types as long values
            builder.registerTypeAdapter(Date.class, new JsonDeserializer<Date>() {
                public Date deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context) throws JsonParseException {
                    return new Date(json.getAsJsonPrimitive().getAsLong());
                }
            });
            Gson gson = builder.create();
            T t = gson.fromJson(o.toString(), clazz);

            return t;

        } catch (Exception e) {
            e.printStackTrace();
            log.error("获取缓存异常key:{}", key, e);
            return null;
        }
    }

    @Override
    public void increment(String key) {
        try {
            log.info("{} 自增操作start,key:{}", Thread.currentThread(), key);
            redisTemplate.boundValueOps(key).increment();
        } catch (Exception e) {
            log.error("自增操作异常,key:{}", key, e);
        }
    }

    @Override
    public Long getLong(String key) {
        return Optional.ofNullable(redisTemplate.boundValueOps(key).get())
                .map(o -> o.toString())
                .map(o -> Long.valueOf(o))
                .orElse(null);
    }

    @Override
    public boolean setIfAbsent(String key, Object value) {
        return redisTemplate.boundValueOps(key).setIfAbsent(value);
    }

    @Override
    public boolean tryLock(String key, String value, long timeout, TimeUnit timeUnit) {
        return redisTemplate.opsForValue().setIfAbsent(key, value, timeout, timeUnit);
    }

    @Override
    public boolean releaseLock(String key, String targetValue) {
        /** 判断是否是key对应的value **/
        String lockValue = redisTemplate.opsForValue().get(key).toString();
        if (lockValue != null && lockValue.equals(targetValue)) {
            return redisTemplate.delete(key);
        }
        return false;
    }

    @Override
    public boolean lpush(String queueName, String value) {
        redisTemplate.boundListOps(queueName).leftPush(value);
        return true;
    }

    @Override
    public String rpop(String queueName) {
        Object o = redisTemplate.boundListOps(queueName).rightPop();
        if (o == null) {
            return null;
        } else {
            return o.toString();
        }
    }

    @Override
    public void permanentPut(String key, Object value) {
        try {
            redisTemplate.boundValueOps(key).set(value);
        } catch (Exception e) {
            log.error("设置缓存异常,key:{}", key, e);
        }
    }

    @Override
    public JSONObject getJSONObject(String key) {
        try {
            Object o = redisTemplate.boundValueOps(key).get();
            if (Objects.isNull(o)) {
                return null;
            }
            return JSON.parseObject(o.toString());
        } catch (Exception e) {
            log.error("获取缓存异常key:{}", key, e);
            return null;
        }
    }

    @Override
    public boolean hasKey(String key) {
        return redisTemplate.hasKey(key);
    }

    @Override
    public Long deleteByPattern(String pattern) {
        try {
            Set<String> keys = redisTemplate.keys(pattern);
            if (keys != null && !keys.isEmpty()) {
                return redisTemplate.delete(keys);
            }
        } catch (Exception ex) {
            log.error("deleteByPattern exception,param {},message {}", pattern, ex.getMessage(), ex);
        }

        return 0L;
    }


//    @Override
//    public <T> void put(String key, T value) {
//        Jedis jedis = null;
//        try {
//            jedis = jedisPool.getResource();
//            jedis.setex(key, (int) TimeUnit.HOURS.toSeconds(24), JSON.toJSONString(value));
//        } catch (Exception ex) {
//            ex.printStackTrace();
//        } finally {
//            //返还到连接池
//            jedis.close();
//        }
//    }
//
//    @Override
//    public <T> void put(String key, T value, int duration) {
//
//        Jedis jedis = null;
//        try {
//            jedis = jedisPool.getResource();
//            jedis.setex(key, duration, JSON.toJSONString(value));
//        } catch (Exception ex) {
//            ex.printStackTrace();
//        } finally {
//            //返还到连接池
//            jedis.close();
//        }
//
//    }
//
//    @Override
//    public long delete(String key) {
//        Jedis jedis = null;
//        long delStatus = 0;
//        try {
//            jedis = jedisPool.getResource();
//            delStatus = jedis.del(key);
//        } catch (Exception ex) {
//            ex.printStackTrace();
//        } finally {
//            //返还到连接池
//            jedis.close();
//        }
//
//        return delStatus;
//    }
//
//    @Override
//    public <T> T get(String key, Class<T> clazz) {
//
//        Jedis jedis = null;
//        T obj = null;
//        try {
//
//            jedis = jedisPool.getResource();
//            String value = jedis.get(key);
//            obj = JSON.parseObject(value, clazz);
//
//        } catch (Exception ex) {
//            ex.printStackTrace();
//        } finally {
//            //返还到连接池
//            jedis.close();
//        }
//
//        return obj;
//    }

}
