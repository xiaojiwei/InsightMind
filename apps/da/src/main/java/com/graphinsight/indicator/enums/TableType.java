package com.graphinsight.indicator.enums;

import com.fasterxml.jackson.annotation.JsonValue;

public enum TableType {

    /**
     * 0-事实表；1-维度表
     */
    FACT(0, "事实表"),
    DIM(1, "维度表");
    private Integer code;

    private String desc;

    TableType(int code, String desc) {
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
