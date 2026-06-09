package com.graphinsight.indicator.enums;

import com.fasterxml.jackson.annotation.JsonValue;

import java.util.Optional;

/**
 * 轴
 */
public enum MVType {

    DIM_DEGENERATE(0,"退化维"),
    WIDGET(1,"查询单图");

    private Integer value;

    private String name;

    MVType(int value, String name) {
        this.value = value;
        this.name = name;
    }

    @JsonValue
    public Integer getValue() {
        return value;
    }

    public boolean inSet(MVType... types) {
        for (MVType type : types) {
            if (this.equals(type)) {
                return true;
            }
        }
        return false;
    }

    public static Optional<MVType> findByInt(Integer value) {
        for (MVType item : MVType.values()) {
            if (item.value.equals(value)) {
                return Optional.of(item);
            }
        }

        return Optional.empty();
    }

    public static Optional<MVType> findByString(String name) {
        for (MVType item : MVType.values()) {
            if (item.name.equals(name)) {
                return Optional.of(item);
            }
        }

        return Optional.empty();
    }

    public static MVType findNullableByString(String name) {
        for (MVType item : MVType.values()) {
            if (item.name.equals(name)) {
                return item;
            }
        }

        return null;
    }

}
