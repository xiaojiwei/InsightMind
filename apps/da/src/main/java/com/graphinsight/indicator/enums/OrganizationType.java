package com.graphinsight.indicator.enums;

import com.fasterxml.jackson.annotation.JsonValue;

import java.util.Optional;

/**
 * 授权元素对象
 */
public enum OrganizationType {

    ORG(0,"组织架构"),
    OPERATE(1,"运营架构"),
    IFS_SALES(4,"IFS销售看板专用架构"),
    HR(6,"飞书架构"),
    USER_PROVINCE_ORG(5,"用户省份人力部门");

    private Integer value;

    private String name;

    OrganizationType(int value, String name) {
        this.value = value;
        this.name = name;
    }

    @JsonValue
    public Integer getValue() {
        return value;
    }

    public boolean inSet(OrganizationType... types) {
        for (OrganizationType type : types) {
            if (this.equals(type)) {
                return true;
            }
        }
        return false;
    }

    public static Optional<OrganizationType> findByInt(Integer value) {
        for (OrganizationType item : OrganizationType.values()) {
            if (item.value.equals(value)) {
                return Optional.of(item);
            }
        }

        return Optional.empty();
    }

    public static Optional<OrganizationType> findByString(String name) {
        for (OrganizationType item : OrganizationType.values()) {
            if (item.name.equals(name)) {
                return Optional.of(item);
            }
        }

        return Optional.empty();
    }

    public static OrganizationType findNullableByString(String name) {
        for (OrganizationType item : OrganizationType.values()) {
            if (item.name.equals(name)) {
                return item;
            }
        }

        return null;
    }

}
