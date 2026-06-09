package com.graphinsight.indicator.enums;

import com.fasterxml.jackson.annotation.JsonValue;

public enum FieldType {

    /**
     * 數據源類型
     */
    MEASURE(0, "指标"),
    DIMENSION(1, "维度");
    private Integer code;

    private String desc;

    FieldType(int code, String desc) {
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
