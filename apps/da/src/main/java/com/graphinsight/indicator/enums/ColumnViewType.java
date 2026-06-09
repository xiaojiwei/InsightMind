package com.graphinsight.indicator.enums;

import java.util.Optional;

public enum ColumnViewType {
    DAY(1, "日"),
    MONTH(3, "月"),
    YEAR(5, "年");

    private Integer value;

    private String name;

    ColumnViewType(int value, String name) {
        this.value = value;
        this.name = name;
    }

    public static Optional<ColumnViewType> findByInt(Integer value) {
        for (ColumnViewType item : ColumnViewType.values()) {
            if (item.value.equals(value)) {
                return Optional.of(item);
            }
        }

        return Optional.empty();
    }

    public static Optional<ColumnViewType> findByString(String name) {
        for (ColumnViewType item : ColumnViewType.values()) {
            if (item.name.equals(name)) {
                return Optional.of(item);
            }
        }

        return Optional.empty();
    }

    public static ColumnViewType findNullableByString(String name) {
        for (ColumnViewType item : ColumnViewType.values()) {
            if (item.name.equals(name)) {
                return item;
            }
        }

        return null;
    }

    public Integer getValue() {
        return value;
    }

    public String getName() {
        return name;
    }

    public void setValue(Integer value) {
        this.value = value;
    }


}
