package com.graphinsight.indicator.enums;

import com.fasterxml.jackson.annotation.JsonValue;

public enum LineStatus {

    OFF(0,"OFF"),
    ON(1, "ON");

    private Integer code;
    private String desc;

    LineStatus(Integer code, String name) {
        this.code = code;
        this.desc = name;
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
