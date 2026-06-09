package com.graphinsight.indicator.enums;

import com.fasterxml.jackson.annotation.JsonValue;

public enum SqlAggFunType {

    LIST(0, "list"),
    SUM(1, "sum"),
    COUNT(2,"count"),
    MAX(3, "max"),
    MIN(4, "min"),
    AVG(5, "avg"),
    DISTINCTCOUNT(6, "distinct_count"),
    PERCENTILE_APPROX50(7, "p50"),
    PERCENTILE_APPROX90(8, "p90"),
    PERCENTILE_APPROX95(9, "p95"),
    PERCENTILE_APPROX99(10, "p99"),
    STDDEV(11, "stddev");

    private Integer code;
    private String desc;


    SqlAggFunType(Integer code, String desc) {
        this.code = code;
        this.desc = desc;
    }

    @JsonValue
    public Integer getCode() {
        return code;
    }

    public void setCode(Integer code) {
        this.code = code;
    }

    public String getDesc() {
        return desc;
    }

    public void setDesc(String desc) {
        this.desc = desc;
    }

    public static SqlAggFunType valueOfDesc(String desc) {
        if (null == desc) {
            return null;
        }
        for(SqlAggFunType type : values()) {
            if (type.desc.equals(desc)) {
                return type;
            }
        }
        return null;
    }

}