package com.graphinsight.indicator.enums;

import com.fasterxml.jackson.annotation.JsonValue;

import java.util.Optional;

/**
 * 轴
 */
public enum AxisType {

    ROW(0,"行轴"),
    COLUMN(1,"列轴");

    private Integer value;

    private String name;

    AxisType(int value, String name) {
        this.value = value;
        this.name = name;
    }

    @JsonValue
    public Integer getValue() {
        return value;
    }

    public boolean inSet(AxisType... types) {
        for (AxisType type : types) {
            if (this.equals(type)) {
                return true;
            }
        }
        return false;
    }

    public static Optional<AxisType> findByInt(Integer value) {
        for (AxisType item : AxisType.values()) {
            if (item.value.equals(value)) {
                return Optional.of(item);
            }
        }

        return Optional.empty();
    }

    public static Optional<AxisType> findByString(String name) {
        for (AxisType item : AxisType.values()) {
            if (item.name.equals(name)) {
                return Optional.of(item);
            }
        }

        return Optional.empty();
    }

    public static AxisType findNullableByString(String name) {
        for (AxisType item : AxisType.values()) {
            if (item.name.equals(name)) {
                return item;
            }
        }

        return null;
    }

}
