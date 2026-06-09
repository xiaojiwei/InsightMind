package com.graphinsight.indicator.enums;

import com.fasterxml.jackson.annotation.JsonValue;

import java.util.Optional;

/**
 * 授权元素对象
 */
public enum AuthFilterParamType {

    STANDARD(0,"标准"),
    CONTEXT(1,"上下文");

    private Integer value;

    private String name;

    public static AuthFilterParamType build(Integer value) {

        if (STANDARD.getValue().equals(value)) {
            return STANDARD;
        } else {
            return CONTEXT;
        }

    }

    AuthFilterParamType(int value, String name) {
        this.value = value;
        this.name = name;
    }

    @JsonValue
    public Integer getValue() {
        return value;
    }

    public boolean inSet(AuthFilterParamType... types) {
        for (AuthFilterParamType type : types) {
            if (this.equals(type)) {
                return true;
            }
        }
        return false;
    }

    public static Optional<AuthFilterParamType> findByInt(Integer value) {
        for (AuthFilterParamType item : AuthFilterParamType.values()) {
            if (item.value.equals(value)) {
                return Optional.of(item);
            }
        }

        return Optional.empty();
    }

    public static Optional<AuthFilterParamType> findByString(String name) {
        for (AuthFilterParamType item : AuthFilterParamType.values()) {
            if (item.name.equals(name)) {
                return Optional.of(item);
            }
        }

        return Optional.empty();
    }

    public static AuthFilterParamType findNullableByString(String name) {
        for (AuthFilterParamType item : AuthFilterParamType.values()) {
            if (item.name.equals(name)) {
                return item;
            }
        }

        return null;
    }

}
