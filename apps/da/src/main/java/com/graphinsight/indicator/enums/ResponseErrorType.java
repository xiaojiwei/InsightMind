package com.graphinsight.indicator.enums;

import com.fasterxml.jackson.annotation.JsonValue;

public enum ResponseErrorType {

    DATA(0, "数据错误"),
    SYSTEM(1, "系统错误");


    private Integer code;
    private String desc;

    ResponseErrorType(Integer code, String desc) {
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
}
