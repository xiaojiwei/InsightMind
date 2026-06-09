package com.graphinsight.indicator.enums;

import com.fasterxml.jackson.annotation.JsonValue;

import java.util.Optional;

/**
 * 员工分配到org的方式
 * 0-飞书架构
 * 1-通过所属部门分配
 * 2-通过负责人(借调)等方式分配
 */
public enum EmployeeOrgType {

    ORG(0,"组织架构"),
    OPERATE(1,"运营架构"),
    PRINCIPAL(2,"负责人架构"),
    SECONDED(3,"借调部门"),
    IFS_SALES(4,"IFS销售看板运营架构"),
    HR(6, "飞书"),
    NEW_TYPE(7, "新商机架构"),
    USER_PROVINCE_ORG(5,"用户省份人力部门");


    private Integer value;

    private String name;

    public static OrganizationType getOrganizationType(EmployeeOrgType employeeOrgType){
        switch (employeeOrgType){
            case ORG:
                return OrganizationType.ORG;
            case HR:
                return OrganizationType.HR;
            case OPERATE:
            case PRINCIPAL:
            case SECONDED:
                return OrganizationType.OPERATE;
            case IFS_SALES:
                return OrganizationType.IFS_SALES;
            case NEW_TYPE:
                return OrganizationType.OPERATE;
            case USER_PROVINCE_ORG:
                return OrganizationType.USER_PROVINCE_ORG;
        }
        return null;
    }

    EmployeeOrgType(int value, String name) {
        this.value = value;
        this.name = name;
    }

    @JsonValue
    public Integer getValue() {
        return value;
    }

    public boolean inSet(EmployeeOrgType... types) {
        for (EmployeeOrgType type : types) {
            if (this.equals(type)) {
                return true;
            }
        }
        return false;
    }

    public static Optional<EmployeeOrgType> findByInt(Integer value) {
        for (EmployeeOrgType item : EmployeeOrgType.values()) {
            if (item.value.equals(value)) {
                return Optional.of(item);
            }
        }

        return Optional.empty();
    }

    public static Optional<EmployeeOrgType> findByString(String name) {
        for (EmployeeOrgType item : EmployeeOrgType.values()) {
            if (item.name.equals(name)) {
                return Optional.of(item);
            }
        }

        return Optional.empty();
    }

    public static EmployeeOrgType findNullableByString(String name) {
        for (EmployeeOrgType item : EmployeeOrgType.values()) {
            if (item.name.equals(name)) {
                return item;
            }
        }

        return null;
    }

}
