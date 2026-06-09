package com.graphinsight.indicator.enums;

import com.fasterxml.jackson.annotation.JsonValue;

/**
 * 角色类型
 */
public enum RoleType {

    ADMIN(0, "空间管理员"),
    OWNER(1, "空间拥有者"),
    ANALYST(2, "分析师"),
    VISITOR(3, "来访者");

    private Integer code;
    private String desc;

    RoleType(int code, String desc) {
        this.code = code;
        this.desc = desc;
    }

    @JsonValue
    public Integer getCode() {
        return code;
    }

    public String getDesc() {
        return desc;
    }
}
