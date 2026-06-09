package com.graphinsight.indicator.enums;

import com.fasterxml.jackson.annotation.JsonValue;

public enum MeasureMonitorStatusEnum {

    OFF(0, "停用"),
    ON(1, "启用");

    private Integer code;
    private String desc;

    MeasureMonitorStatusEnum(Integer code, String desc) {
        this.code = code;
        this.desc = desc;
    }

    @JsonValue
    public Integer getCode() {
        return code;
    }

}
