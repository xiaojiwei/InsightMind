package com.graphinsight.indicator.enums;

import com.fasterxml.jackson.annotation.JsonValue;

import java.util.Objects;
import java.util.Optional;

/**
 * 授权元素对象
 */
public enum AuthObjectType {

    ORG(0,"组织架构"),
    EMPLOYEE(1,"人员"),
    OPERATE(2,"运营架构"),
    POST(3,"RB岗位");

    private Integer value;

    private String name;

    AuthObjectType(int value, String name) {
        this.value = value;
        this.name = name;
    }

    @JsonValue
    public Integer getValue() {
        return value;
    }

    public boolean inSet(AuthObjectType... types) {
        for (AuthObjectType type : types) {
            if (this.equals(type)) {
                return true;
            }
        }
        return false;
    }

    public static Optional<AuthObjectType> findByInt(Integer value) {
        for (AuthObjectType item : AuthObjectType.values()) {
            if (item.value.equals(value)) {
                return Optional.of(item);
            }
        }

        return Optional.empty();
    }

    public static Optional<AuthObjectType> findByString(String name) {
        for (AuthObjectType item : AuthObjectType.values()) {
            if (item.name.equals(name)) {
                return Optional.of(item);
            }
        }

        return Optional.empty();
    }

    public static AuthObjectType getTypeByOrgType(Integer orgType) {
        if(Objects.equals(OrganizationType.ORG.getValue(),orgType)){
            return AuthObjectType.ORG;
        } else if(Objects.equals(OrganizationType.OPERATE.getValue(),orgType)){
            return AuthObjectType.OPERATE;
        } else {
            return AuthObjectType.EMPLOYEE;
        }
    }


    public static AuthObjectType findNullableByString(String name) {
        for (AuthObjectType item : AuthObjectType.values()) {
            if (item.name.equals(name)) {
                return item;
            }
        }

        return null;
    }

}
