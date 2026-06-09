package com.graphinsight.indicator.enums;

import com.fasterxml.jackson.annotation.JsonValue;

public enum MemberType {

    /**
     * 成员类型,维度、指标、指标分组 Measure
     */
    DIMENSION(0, "维度"),
    MEASURE(1, "指标"),
    MEASURE_GROUP(2, "指标分组"),
    MEASURE_VALUE(3, "指标值");

    private Integer code;
    private String desc;

    MemberType(Integer code, String desc) {
        this.code = code;
        this.desc = desc;
    }

    @JsonValue
    public Integer getCode() {
        return code;
    }

    public String getDesc() {
        return desc;
    }

    public void setDesc(String desc) {
        this.desc = desc;
    }

    public void setCode(Integer code) {
        this.code = code;
    }

}
