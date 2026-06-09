package com.graphinsight.indicator.enums;

import com.fasterxml.jackson.annotation.JsonValue;

/**
 * 授权元素对象
 */
public enum IndicatorAuthObjectType {

    ORG(0,"组织架构"),
    EMPLOYEE(1,"人员"),
    OPERATE(2,"运营架构"),
    POST(3,"岗位");

    private Integer code;

    private String name;

    public static IndicatorAuthObjectType getByCode(Integer code){
        if (code == null) {
            return null;
        }
        IndicatorAuthObjectType[] values = IndicatorAuthObjectType.values();
        for (IndicatorAuthObjectType value : values) {
            if (value.code.intValue() == code.intValue()) {
                return value;
            }
        }
        return null;
    }

    IndicatorAuthObjectType(int code, String name) {
        this.code = code;
        this.name = name;
    }

    @JsonValue
    public Integer getCode() {
        return code;
    }


}
