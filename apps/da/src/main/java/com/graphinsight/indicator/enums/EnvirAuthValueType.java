package com.graphinsight.indicator.enums;

import com.fasterxml.jackson.annotation.JsonValue;

import java.util.Optional;

/**
 * 授权权限变量,
 * 这里特殊用name。
 */
public enum EnvirAuthValueType {

    CITY(0,"%CITY%"),
    PROV(1,"%PROV%"),
    STORE(2, "%STORE%");

    private Integer value;

    private String name;

    EnvirAuthValueType(int value, String name) {
        this.value = value;
        this.name = name;
    }

    public Integer getValue() {
        return value;
    }

    @JsonValue
    public String getName() {
        return name;
    }

    public boolean inSet(EnvirAuthValueType... types) {
        for (EnvirAuthValueType type : types) {
            if (this.equals(type)) {
                return true;
            }
        }
        return false;
    }

    public static Optional<EnvirAuthValueType> findByInt(Integer value) {
        for (EnvirAuthValueType item : EnvirAuthValueType.values()) {
            if (item.value.equals(value)) {
                return Optional.of(item);
            }
        }

        return Optional.empty();
    }

    public static Optional<EnvirAuthValueType> findByString(String name) {
        for (EnvirAuthValueType item : EnvirAuthValueType.values()) {
            if (item.name.equals(name)) {
                return Optional.of(item);
            }
        }

        return Optional.empty();
    }

    public static EnvirAuthValueType findNullableByString(String name) {
        for (EnvirAuthValueType item : EnvirAuthValueType.values()) {
            if (item.name.equals(name)) {
                return item;
            }
        }

        return null;
    }

}
