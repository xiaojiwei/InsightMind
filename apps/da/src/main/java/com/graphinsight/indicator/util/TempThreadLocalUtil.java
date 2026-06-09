package com.graphinsight.indicator.util;


import java.util.HashMap;

public class TempThreadLocalUtil {

    //把构造函数私有，外面不能new，只能通过下面两个方法操作
    private TempThreadLocalUtil() {}

    private static final ThreadLocal<HashMap> LOCAL_MAP = new ThreadLocal<HashMap>();

    public static void set(Object key, Object value){

        HashMap<Object, Object> map = LOCAL_MAP.get();
        if (null == map) {
            map = new HashMap<>();
            LOCAL_MAP.set(map);
        }

        if (key == null || value == null) {
            map.put("error", "null");
        } else {
            map.put(key, value);
        }

    }

    public static Object get(Object key) {
        HashMap<Object, Object> map = LOCAL_MAP.get();
        if (null == map) {
            map = new HashMap<>();
            LOCAL_MAP.set(map);
        }
        return map.get(key);
    }

}
