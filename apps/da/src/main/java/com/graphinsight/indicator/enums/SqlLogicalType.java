package com.graphinsight.indicator.enums;

import com.fasterxml.jackson.annotation.JsonValue;

import java.util.Objects;

public enum SqlLogicalType {

    /**
     * and or
     */
    AND(0, "and"),
    OR(1, "or");

    private Integer code;

    private String desc;

    SqlLogicalType(Integer code, String desc) {
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

    public String getDesc() {
        return desc;
    }

    public void setDesc(String desc) {
        this.desc = desc;
    }


    public static SqlLogicalType getTypeByCode(Integer code){
        SqlLogicalType[] values = SqlLogicalType.values();
        for (SqlLogicalType value : values) {
            if (Objects.equals(code,value.getCode())){
                return value;
            }
        }
        return null;
    }

}
