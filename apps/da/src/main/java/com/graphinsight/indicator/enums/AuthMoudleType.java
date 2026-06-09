package com.graphinsight.indicator.enums;

import com.fasterxml.jackson.annotation.JsonValue;

/**
 * Author: lixiaolong
 * Date: 2022/11/28
 * Desc: 权限的模块类型
 */
public enum AuthMoudleType {

    PORTAL(0, "门户"),
    DASHBOARD(1, "看板");

    private Integer code;

    private String desc;

    AuthMoudleType(int code, String desc) {
        this.code = code;
        this.desc = desc;
    }

    public static AuthMoudleType getByCode(Integer code){
        if (code == null) {
            return null;
        }
        AuthMoudleType[] values = AuthMoudleType.values();
        for (AuthMoudleType value : values) {
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
