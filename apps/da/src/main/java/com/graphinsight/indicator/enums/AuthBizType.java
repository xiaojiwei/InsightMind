package com.graphinsight.indicator.enums;

import com.fasterxml.jackson.annotation.JsonValue;

/**
 * Date: 2022/11/28
 * Desc: 门户业务类型
 */
public enum AuthBizType {

    PORTAL(0, "门户"),
    MENU(1, "菜单");

    private Integer code;

    private String desc;

    AuthBizType(int code, String desc) {
        this.code = code;
        this.desc = desc;
    }

    public static AuthBizType getByCode(Integer code){
        if (code == null) {
            return null;
        }
        AuthBizType[] values = AuthBizType.values();
        for (AuthBizType value : values) {
            if (value.code.intValue() == code.intValue()) {
                return value;
            }
        }
        return null;
    }

    @JsonValue
    public Integer getCode() {
        return code;
    }

    public String getDesc() {
        return desc;
    }

}
