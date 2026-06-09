package com.graphinsight.indicator.util;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.google.common.hash.HashCode;
import com.google.common.hash.HashFunction;
import com.google.common.hash.Hashing;
import com.graphinsight.indicator.auto.entity.Department;
import com.graphinsight.indicator.auto.mapper.DepartmentMapper;
import com.graphinsight.indicator.enums.AuthObjectType;
import com.graphinsight.indicator.model.DataSource;
import com.graphinsight.indicator.model.Dimension;
import com.graphinsight.indicator.model.Measure;
import com.graphinsight.indicator.model.Table;
import com.graphinsight.indicator.service.IndicatorService;
import com.graphinsight.indicator.service.IndicatorUserService;
import com.graphinsight.indicator.service.RedisCacheService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

@Service
public class MemCacheUtils {

    private static Cache<Object, Object> cache = CacheBuilder.newBuilder()
                .initialCapacity(10000)
                .concurrencyLevel(20)
                .expireAfterAccess(10, TimeUnit.DAYS)
                .build();

//    private static Map<Object, Object> cache = new ConcurrentHashMap<>();
//
//    @Autowired
//    private RedisCacheService redisCacheService;

    public void putMeasure(String key, Object value) {
//        this.redisCacheService.put(key, value);
        cache.put(key, value);
    }

    public static void put(String key, Object value) {
        cache.put(key, value);
    }

    public static Object get(String key) {
        return cache.getIfPresent(key);
//        return cache.get(key);
    }



    static final String CACHE_DEPART_KEY = "departments";

//    static Lock BELONG_LOCK = new ReentrantLock();

    static Long BELONG_TIME = System.currentTimeMillis();

    public static Boolean getIsExist(String username,String targetCode, AuthObjectType authObjectType, IndicatorUserService indicatorUserService) {

        String key = username + targetCode + authObjectType.toString();
        Object obj = cache.getIfPresent(key);
//        Object obj = cache.get(key);
        Boolean isExist = false;
        if (null == obj) {

            isExist = indicatorUserService.belongDept(username, targetCode, authObjectType);
            cache.put(key, isExist);

        } else {
            isExist = (Boolean) obj;
            new Thread(() -> {

                Long dev = System.currentTimeMillis() - BELONG_TIME;
                if (dev > 2000000L) {
                    try {
                        Boolean exist = indicatorUserService.belongDept(username, targetCode, authObjectType);
                        cache.put(key, exist);
                    } finally {
                    }

                }

            }).start();

        }

        return isExist;

    }

    public static List<Department> getDepartment(final DepartmentMapper departmentMapper) {

//        Object obj = cache.getIfPresent(CACHE_DEPART_KEY);
        Object obj = get(CACHE_DEPART_KEY);
        if (obj != null) {
            List<Department> departmentList = (List<Department>) obj;
            new Thread(() -> {

                Long dev = System.currentTimeMillis() - LOCK_DEPT_LAST_TIME;
                if (dev > 172800000l) {
                    List<Department> departments = departmentMapper.selectList(null);
                    LOCK_DEPT_LAST_TIME = System.currentTimeMillis();
                    cache.put(CACHE_DEPART_KEY, departments);
                }

            }).start();

            return departmentList;
        } else {
            List<Department> departments = departmentMapper.selectList(null);
            cache.put(CACHE_DEPART_KEY, departments);
            LOCK_DEPT_LAST_TIME = System.currentTimeMillis();
            return departments;
        }

    }

    public  Measure getMeasure(final String measCode) {
        //action
//        Object value = this.redisCacheService.get(measCode, Measure.class);
        Object value = cache.getIfPresent(measCode);
        Measure measure = null;
        if (null != value) {
            measure = (Measure) value;
            measure = CloneUtils.clone(measure);
        }

        return measure;
    }

    private static HashFunction hf = Hashing.md5();
    private static Charset charset = Charset.forName("UTF-8");

    public static String md5(String data) {
        HashCode hash = hf.newHasher().putString(data, charset).hash();
        return hash.toString();
    }

//    static Lock LOCK = new ReentrantLock();
//    static Lock LOCK_DEPT = new ReentrantLock();

    static Long LOCK_DEPT_LAST_TIME = System.currentTimeMillis();
    static Long LOCK_LAST_TIME = System.currentTimeMillis();

    public static Dimension getDimensionTableInfo(IndicatorService indicatorService, final String dimCode) {

//        return indicatorService.getDimensionTableInfo(dimCode);

        Object value = MemCacheUtils.get(dimCode);
        if (null == value) {
            value = indicatorService.getDimensionTableInfo(dimCode);
            MemCacheUtils.put(dimCode, value);
            return (Dimension) value;

        } else {

            new Thread(() -> {

                Long dec = System.currentTimeMillis() - LOCK_LAST_TIME;
               if (dec > 2000000L) {
                   final Dimension dim = indicatorService.getDimensionTableInfo(dimCode);
                   MemCacheUtils.put(dimCode, dim);
                   LOCK_LAST_TIME = System.currentTimeMillis();
               }

            }).start();

        }

        return (Dimension) value;

    }

}
