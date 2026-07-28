package com.graphinsight.indicator.enums;

import com.fasterxml.jackson.annotation.JsonValue;

/**
 * Date: 2022/11/3
 * Desc: 节点的类型
 */
public enum DismantlingConfigTreeRegionType {

    DIMENSION_DRILL_DOWN(0, "维度下钻"),
    MEASURE_DISMANTLING(1, "指标拆解");

    private Integer code;
    private String desc;

    @JsonValue
    public Integer getCode() {
        return code;
    }

    public String getDesc() {
        return desc;
    }

    DismantlingConfigTreeRegionType(Integer code, String desc) {
        this.code = code;
        this.desc = desc;
    }
}
