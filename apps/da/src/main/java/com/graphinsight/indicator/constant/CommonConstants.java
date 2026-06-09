package com.graphinsight.indicator.constant;

import java.util.HashMap;
import java.util.Map;

/**
 * @Author: 陈永量
 * @Date: 2023/12/4
 */
public class CommonConstants {

    /**
     * 逗号字符
     */
    public static final String COMMA_CHAR = ",";

    public static final Map<String, String> dateReplaceMAP = new HashMap<>();

    static {
        dateReplaceMAP.put("年", "year");
        dateReplaceMAP.put("月", "month");
        dateReplaceMAP.put("个月", "month");
        dateReplaceMAP.put("天", "day");
    }

    public static final Map<String, String> dateFormatMAP = new HashMap<>();


    public static final Map<String, String> dictCodeMap = new HashMap<>();

    static {
        dictCodeMap.put("净锁单量", "MEAS_d93e71a5fde84f968e3e2e6696297f6c");
        dictCodeMap.put("区域名称", "DIM_213c99569f204a0aa4fa199691a47cb9");
        dictCodeMap.put("车辆类型编码", "DIM_5a9b54c7ae1d45649c1b9bc9a074617d");
        dictCodeMap.put("年", "DIM_0a61b0022ae241e7a400399e97dc1e63");
        dictCodeMap.put("月", "DIM_4e41a99d4b964cc0a66dd7c02356c473");
        dictCodeMap.put("日", "DIM_a15f9bcd0235428fbaf164b584f8055f");
    }

    public static final Map<String, String> CarCodeMap = new HashMap<>();

    static {
        CarCodeMap.put("L9", "X01");
        CarCodeMap.put("L8", "X02");
        CarCodeMap.put("L7", "X03");
        CarCodeMap.put("l9", "X01");
        CarCodeMap.put("l8", "X02");
        CarCodeMap.put("l7", "X03");
    }


    public static final Map<String, String> DateKeyMap = new HashMap<>();

    static {
        DateKeyMap.put("分天", "日");
        DateKeyMap.put("分日", "日");
        DateKeyMap.put("分月", "月");
        DateKeyMap.put("分年", "年");
        DateKeyMap.put("按日", "日");
        DateKeyMap.put("按月", "月");
        DateKeyMap.put("按年", "年");
        DateKeyMap.put("按照日", "月");
        DateKeyMap.put("按照月", "月");
        DateKeyMap.put("按照年", "年");
        DateKeyMap.put("各年", "年");
        DateKeyMap.put("各个月", "月");
        DateKeyMap.put("各月", "月");
        DateKeyMap.put("各日", "日");
        DateKeyMap.put("每年", "年");
        DateKeyMap.put("每月", "月");
        DateKeyMap.put("每个月", "月");
        DateKeyMap.put("个月", "月");
        DateKeyMap.put("月份", "月");
        DateKeyMap.put("个月份", "月");
        DateKeyMap.put("每日", "日");
        DateKeyMap.put("每天", "日");
        DateKeyMap.put("月趋势", "月");
        DateKeyMap.put("年趋势", "年");
        DateKeyMap.put("日趋势", "日");
        DateKeyMap.put("月环比", "月");
        DateKeyMap.put("年环比", "年");
        DateKeyMap.put("日环比", "日");
        DateKeyMap.put("月同比", "月");
        DateKeyMap.put("年同比", "年");
        DateKeyMap.put("日同比", "日");
        DateKeyMap.put("日期", "日");
    }

    public static final Map<String, String> CarTypeMap = new HashMap<>();

    static {
        CarTypeMap.put("车型", "123");
        CarTypeMap.put("各车型", "123");
        CarTypeMap.put("每个车型", "123");
//        CarTypeMap.put("省份", "省份");
    }

    public static final Map<String, String> AreaTypeMap = new HashMap<>();

    static {
        CarTypeMap.put("每个省", "123");
        CarTypeMap.put("每个省份", "123");
        CarTypeMap.put("每个城市", "123");
        CarTypeMap.put("每个市", "123");
        CarTypeMap.put("每个县", "123");
        CarTypeMap.put("每个县城", "123");
    }

    public static final Map<String, String> DateDimCodeMap = new HashMap<>();

    static {
        DateDimCodeMap.put("月", "DIM_4e41a99d4b964cc0a66dd7c02356c473");
        DateDimCodeMap.put("年", "DIM_0a61b0022ae241e7a400399e97dc1e63");
        DateDimCodeMap.put("日", "DIM_a15f9bcd0235428fbaf164b584f8055f");
    }

    public static final Map<String, String> SortMap = new HashMap<>();

    static {
        SortMap.put("最高", "1");
        SortMap.put("最低", "2");
    }


    public static final Map<String, Map<String, Integer>> rateDateMap = new HashMap<>();


    // 年 只有环比 4
    // 月 环比 1 同比 4
    // 日 环比 1 月同比 3 年同比4
    static {

        Map<String, Integer> subMap = new HashMap<>();
        subMap.put("年环比", 4); // 示例数据
        rateDateMap.put("年", subMap);

        Map<String, Integer> subMap2 = new HashMap<>();
        subMap2.put("月环比", 1); // 示例数据
        subMap2.put("月同比", 4);

        rateDateMap.put("月", subMap2);

        Map<String, Integer> subMap3 = new HashMap<>();
        subMap3.put("日环比", 1); // 示例数据
        subMap3.put("日同比", 3); // 日同比 默认使用月的同比
        subMap3.put("月同比", 3); // 示例数据
        subMap3.put("年同比", 4);

        rateDateMap.put("日", subMap3);
    }


    public static final Map<String, String> DateDimCodeNameMap = new HashMap<>();

    static {
        DateDimCodeNameMap.put("DIM_4e41a99d4b964cc0a66dd7c02356c473", "月");
        DateDimCodeNameMap.put("DIM_0a61b0022ae241e7a400399e97dc1e63", "年");
        DateDimCodeNameMap.put("DIM_a15f9bcd0235428fbaf164b584f8055f", "日");
    }


    public static final Integer DETAIL_DEFAULT_COUNT = 1000;

    public static final String LARGE_NUMERALS = "一二两三四五六七八九十";
}
