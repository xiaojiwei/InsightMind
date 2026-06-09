package com.graphinsight.indicator.enums;

import com.fasterxml.jackson.annotation.JsonValue;

public enum ColumnDataType {

    BIGINT(0, "长整形"),
    VARCHAR(1, "字符串");

    private Integer code;
    private String desc;

    ColumnDataType(Integer code, String desc) {
        this.code = code;
        this.desc = desc;
    }

    @JsonValue
    public Integer getCode() {
        return code;
    }

}
