package com.graphinsight.indicator.enums;

import com.fasterxml.jackson.annotation.JsonValue;

import java.util.Optional;

/**
 * 同环比显示列
 *
 */
public enum RatioColumnType {

    IN(0,"内嵌"),
    NEW(1,"新增列");

    private Integer value;

    private String name;

    RatioColumnType(int value, String name) {
        this.value = value;
        this.name = name;
    }

    @JsonValue
    public Integer getValue() {
        return value;
    }

    public boolean inSet(RatioColumnType... types) {
        for (RatioColumnType type : types) {
            if (this.equals(type)) {
                return true;
            }
        }
        return false;
    }

    public static Optional<RatioColumnType> findByInt(Integer value) {
        for (RatioColumnType item : RatioColumnType.values()) {
            if (item.value.equals(value)) {
                return Optional.of(item);
            }
        }

        return Optional.empty();
    }

    public static Optional<RatioColumnType> findByString(String name) {
        for (RatioColumnType item : RatioColumnType.values()) {
            if (item.name.equals(name)) {
                return Optional.of(item);
            }
        }

        return Optional.empty();
    }

    public static RatioColumnType findNullableByString(String name) {
        for (RatioColumnType item : RatioColumnType.values()) {
            if (item.name.equals(name)) {
                return item;
            }
        }

        return null;
    }

}
