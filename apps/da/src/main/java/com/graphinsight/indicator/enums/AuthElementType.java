package com.graphinsight.indicator.enums;

import com.fasterxml.jackson.annotation.JsonValue;

import java.util.Optional;

/**
 * 授权元素对象
 */
public enum AuthElementType {

    MEASURE(0,"指标"),
    DIMENSION(1,"维度");

    private Integer value;

    private String name;

    public static AuthElementType build(Integer value) {

        if (MEASURE.getValue().equals(value)) {
            return MEASURE;
        } else {
            return DIMENSION;
        }

    }

    AuthElementType(int value, String name) {
        this.value = value;
        this.name = name;
    }

    @JsonValue
    public Integer getValue() {
        return value;
    }

    public boolean inSet(AuthElementType... types) {
        for (AuthElementType type : types) {
            if (this.equals(type)) {
                return true;
            }
        }
        return false;
    }

    public static Optional<AuthElementType> findByInt(Integer value) {
        for (AuthElementType item : AuthElementType.values()) {
            if (item.value.equals(value)) {
                return Optional.of(item);
            }
        }

        return Optional.empty();
    }

    public static Optional<AuthElementType> findByString(String name) {
        for (AuthElementType item : AuthElementType.values()) {
            if (item.name.equals(name)) {
                return Optional.of(item);
            }
        }

        return Optional.empty();
    }

    public static AuthElementType findNullableByString(String name) {
        for (AuthElementType item : AuthElementType.values()) {
            if (item.name.equals(name)) {
                return item;
            }
        }

        return null;
    }

}
