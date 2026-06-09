package com.graphinsight.indicator.enums;

import com.fasterxml.jackson.annotation.JsonValue;

import java.util.Optional;

public enum ItemType {

    /**
     * 0-指标 OPERAND
     * 1-常数 CONSTANT
     * 2-操作符 OPERATOR
     */
    OPERAND(0,"OPERAND"),
    CONSTANT(1,"CONSTANT"),
    OPERATOR(2,"OPERATOR");

    private Integer value;

    private String name;

    ItemType(int value, String name) {
        this.value = value;
        this.name = name;
    }


    public boolean inSet(DimType... types) {
        for (DimType type : types) {
            if (this.equals(type)) {
                return true;
            }
        }
        return false;
    }

    @JsonValue
    public Integer getValue() {
        return this.value;
    }

    public String getName() {
        return this.name;
    }

    public static Optional<ItemType> findByInt(Integer value) {
        for (ItemType item : ItemType.values()) {
            if (item.value.equals(value)) {
                return Optional.of(item);
            }
        }

        return Optional.empty();
    }

    public static Optional<ItemType> findByString(String name) {
        for (ItemType item : ItemType.values()) {
            if (item.name.equals(name)) {
                return Optional.of(item);
            }
        }

        return Optional.empty();
    }

    public static ItemType findNullableByString(String name) {
        for (ItemType item : ItemType.values()) {
            if (item.name.equals(name)) {
                return item;
            }
        }

        return null;
    }
}
