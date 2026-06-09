package com.graphinsight.indicator.enums;

import com.fasterxml.jackson.annotation.JsonValue;

public enum DashboardStatus {

    OFFLINE(0,"下线"),
    ONLINE(1, "在线");

    private Integer code;
    private String desc;

    DashboardStatus(Integer code, String name) {
        this.code = code;
        this.desc = name;
    }

    @JsonValue
    public Integer getCode() {
        return code;
    }

    public String getDesc() {
        return desc;
    }

    public void setDesc(String desc) {
        this.desc = desc;
    }

}
