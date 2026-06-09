package com.graphinsight.indicator.enums;

import com.fasterxml.jackson.annotation.JsonValue;

import java.util.Optional;

/**
 * 授权元素对象
 */
public enum FilterType {

    FILTER(0,"筛选项"),
    CHILDREN(1, "子集");

    private Integer value;

    private String name;

    public static FilterType build(Integer value) {

        if (FILTER.getValue().equals(value)) {
            return FILTER;
        } else {
            return CHILDREN;
        }

    }

    FilterType(int value, String name) {
        this.value = value;
        this.name = name;
    }

    @JsonValue
    public Integer getValue() {
        return value;
    }

    public boolean inSet(FilterType... types) {
        for (FilterType type : types) {
            if (this.equals(type)) {
                return true;
            }
        }
        return false;
    }

    public static Optional<FilterType> findByInt(Integer value) {
        for (FilterType item : FilterType.values()) {
            if (item.value.equals(value)) {
                return Optional.of(item);
            }
        }

        return Optional.empty();
    }

    public static Optional<FilterType> findByString(String name) {
        for (FilterType item : FilterType.values()) {
            if (item.name.equals(name)) {
                return Optional.of(item);
            }
        }

        return Optional.empty();
    }

    public static FilterType findNullableByString(String name) {
        for (FilterType item : FilterType.values()) {
            if (item.name.equals(name)) {
                return item;
            }
        }

        return null;
    }

}
