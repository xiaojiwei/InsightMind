package com.graphinsight.indicator.enums;

import com.fasterxml.jackson.annotation.JsonValue;

import java.util.Optional;

/**
 * 员工分配到org的方式
 * 0-飞书架构
 * 1-通过所属部门分配
 * 2-通过负责人(借调)等方式分配
 */
public enum EmployeeType {

    LIXIANG(0,"理想汽车内部员工"),
    PARTNER(1,"合作伙伴"),
    OUTSOURCE(2,"外援");

    private Integer value;

    private String name;


    EmployeeType(int value, String name) {
        this.value = value;
        this.name = name;
    }

    @JsonValue
    public Integer getValue() {
        return value;
    }


    public static Optional<EmployeeType> findByInt(Integer value) {
        for (EmployeeType item : EmployeeType.values()) {
            if (item.value.equals(value)) {
                return Optional.of(item);
            }
        }

        return Optional.empty();
    }

    public static Optional<EmployeeType> findByString(String name) {
        for (EmployeeType item : EmployeeType.values()) {
            if (item.name.equals(name)) {
                return Optional.of(item);
            }
        }

        return Optional.empty();
    }

    public static EmployeeType findNullableByString(String name) {
        for (EmployeeType item : EmployeeType.values()) {
            if (item.name.equals(name)) {
                return item;
            }
        }

        return null;
    }

}
