package com.graphinsight.indicator.enums;

import com.fasterxml.jackson.annotation.JsonValue;

import java.util.Optional;

public enum DimType {

    DEGENERATE_DIM(0,"退化维"),
    STD_WITHOUT_TABLE(1,"标准维无维表"),
    STD_WITH_TABLE(2,"标准维有维表"),
    CUSTOM(4,"用户自定义表达式维度");

    private Integer code;

    private String name;

    DimType(int code, String name) {
        this.code = code;
        this.name = name;
    }

    @JsonValue
    public Integer getValue() {
        return code;
    }

    public String getName() {
        return name;
    }

    public boolean inSet(DimType... types) {
        for (DimType type : types) {
            if (this.equals(type)) {
                return true;
            }
        }
        return false;
    }

    public static Optional<DimType> findByInt(Integer value) {
        for (DimType item : DimType.values()) {
            if (item.code.equals(value)) {
                return Optional.of(item);
            }
        }

        return Optional.empty();
    }

    public static Optional<DimType> findByString(String name) {
        for (DimType item : DimType.values()) {
            if (item.name.equals(name)) {
                return Optional.of(item);
            }
        }

        return Optional.empty();
    }

    public static DimType findNullableByString(String name) {
        for (DimType item : DimType.values()) {
            if (item.name.equals(name)) {
                return item;
            }
        }

        return null;
    }

}
