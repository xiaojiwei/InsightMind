package com.graphinsight.indicator.enums;

import com.fasterxml.jackson.annotation.JsonValue;

import java.util.Objects;

public enum  SqlOprType {

    /**
     * where sql
     */
    IN(0, "in"),
    NOTIN(1, "not-in"),
    BETEEN(2, "beteen"),
    GREATER_THAN(3, "greater-than"),
    SMALLER_THAN(4, "smaller-than"),
    GREATER_THAN_OR_EQUAL(5, "greater-than-or-equal"),
    SMALLER_THAN_OR_EQUAL(6, "smaller-than-or-equal"),
    EQUAL(7, "equal"),
    NOT_EQUAL(8, "not-equal"),
    LIKE(9, "like"),
    LIKE_NO_INCLUDE(10, "like-no-include"),
    EQUAL_NULL(11, "equal-null"),
    EQUAL_NO_NULL(12, "equal-not-null"),
    EQUAL_NULL_CHART(13, "equal-null-chart"),
    EQUAL_NO_NULL_CHART(14, "equal-not-null-chart"),
    DERIVED_EQUAL_ID(15, "derived-equal-id"),
    IS_NULL(16, "isNull");

    private Integer code;

    private String desc;

    SqlOprType(Integer code, String desc) {
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

    public static SqlOprType getTypeByCode(Integer code){
        SqlOprType[] values = SqlOprType.values();
        for (SqlOprType value : values) {
            if (Objects.equals(code,value.getCode())){
                return value;
            }
        }
        return null;
    }

    public static SqlOprType getTypeByDesc(String desc){
        SqlOprType[] values = SqlOprType.values();
        for (SqlOprType value : values) {
            if (Objects.equals(desc,value.getDesc())){
                return value;
            }
        }
        return null;
    }

}
