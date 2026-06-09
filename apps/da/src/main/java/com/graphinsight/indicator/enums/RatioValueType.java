package com.graphinsight.indicator.enums;

import com.fasterxml.jackson.annotation.JsonValue;

public enum RatioValueType {

    /**
     * 同环比取数方式
     */
    VALUE(0, "值"),
    RATIO(1, "率");

    private Integer code;
    private String desc;

    RatioValueType(Integer code, String desc) {
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

}
