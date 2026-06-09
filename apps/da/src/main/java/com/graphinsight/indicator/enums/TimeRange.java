package com.graphinsight.indicator.enums;

import com.fasterxml.jackson.annotation.JsonValue;

import java.util.Arrays;
import java.util.Objects;

public enum TimeRange {

    /**
     * 时间段
     */
    NULL(0, "非日期"),
    DATE(1, "自定义日期"),
    YESTERDAY(2,"昨日"),
    WEEK(3, "近7日"),
    LAST_15_DAY(4, "近15天"),
    ONE_MONTH(5, "近1月"),
    TRIPLE_MONTH(6, "近3月"),
    HALF_YEAR(7, "近半年"),
    ONE_YEAR(8, "近1年"),

    // 周
    LAST_1_WEEK(16, "近1周"),
    LAST_7_WEEK(17, "近7周"),
    LAST_26_WEEK(18,"近26周"),
    LAST_52_WEEK(19,"近52周"),
    CUSTOM_WEEK(20,"自定义周"),
    // 月
    LAST_1_MONTH(21,"近1个月"),
    LAST_7_MONTH(22,"近7个月"),
    LAST_26_MONTH(23,"近26个月"),
    LAST_52_MONTH(24,"近52个月"),
    CUSTOM_MONTH(25,"自定义月"),
    // 小时
    LAST_6_HOUR(26,"近6小时"),
    LAST_12_HOUR(27,"近12小时"),
    LAST_24_HOUR(28,"近24小时"),
    LAST_48_HOUR(29,"近48小时"),
    LAST_168_HOUR(30,"近168小时"),
    LAST_360_HOUR(31,"近360小时"),
    CUSTOM_HOUR(32,"自定义小时");

    private String desc;
    private Integer code;

    @JsonValue
    public Integer getCode() {
        return code;
    }

    public void setCode(Integer code) {
        this.code = code;
    }

    TimeRange(Integer code, String desc) {
        this.code = code;
        this.desc = desc;
    }

    public String getDesc() {
        return desc;
    }

    public void setDesc(String desc) {
        this.desc = desc;
    }

    public boolean isHourRange() {
        return Arrays.asList(LAST_6_HOUR, LAST_12_HOUR, LAST_24_HOUR, LAST_48_HOUR, LAST_168_HOUR, LAST_360_HOUR, CUSTOM_HOUR).contains(this);
    }

    public boolean isHour() {
        return Arrays.asList(LAST_6_HOUR, LAST_12_HOUR, LAST_24_HOUR, LAST_48_HOUR, LAST_168_HOUR, LAST_360_HOUR, CUSTOM_HOUR).contains(this);
    }

    public boolean isDayRange() {
        return Arrays.asList(DATE, WEEK, LAST_15_DAY, ONE_MONTH, TRIPLE_MONTH, HALF_YEAR, ONE_YEAR).contains(this);
    }

    public boolean isDay() {
        return Arrays.asList(DATE, YESTERDAY, WEEK, LAST_15_DAY, ONE_MONTH, TRIPLE_MONTH, HALF_YEAR, ONE_YEAR).contains(this);
    }

    public int compare(TimeRange timeRange) {
        return Integer.compare(this.ordinal(), timeRange.ordinal());
    }

    public static TimeRange getTypeByCode(Integer code){
        TimeRange[] values = TimeRange.values();
        for (TimeRange value : values) {
            if (Objects.equals(code,value.getCode())){
                return value;
            }
        }
        return null;
    }

}
