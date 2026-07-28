package com.graphinsight.indicator.enums;

import com.fasterxml.jackson.annotation.JsonValue;

/**
 * Date: 2022/2/10
 * Desc:
 */
public enum MeasureMonitorStatusType {
    OFF(0),
    ON(1);

    Integer code;

    MeasureMonitorStatusType(Integer code) {
        this.code = code;
    }

    @JsonValue
    public int getCode() {
        return code;
    }

}
