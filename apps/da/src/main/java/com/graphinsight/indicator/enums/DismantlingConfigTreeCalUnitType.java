package com.graphinsight.indicator.enums;

import com.fasterxml.jackson.annotation.JsonValue;

/**
 * Author: lixiaolong
 * Date: 2022/11/3
 * Desc: 节点的类型
 */
public enum DismantlingConfigTreeCalUnitType {
    NUMERICAL_VALUE(0, "指标数值"),
    PROPORTION(1, "指标占比"),
    OPERATOR(2, "运算符");

    private Integer code;
    private String desc;

    @JsonValue
    public Integer getCode() {
        return code;
    }

    public String getDesc() {
        return desc;
    }

    DismantlingConfigTreeCalUnitType(Integer code, String desc) {
        this.code = code;
        this.desc = desc;
    }
}
