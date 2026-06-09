package com.graphinsight.indicator.enums;

import com.fasterxml.jackson.annotation.JsonValue;

public enum AvailableType {

    AVAILABLE(1, "可用"),
    NOT_AVALIABLE(0, "不可用");
    private Integer code;

    private String desc;

    AvailableType(int code, String desc) {
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
}
