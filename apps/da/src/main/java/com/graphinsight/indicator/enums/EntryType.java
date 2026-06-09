package com.graphinsight.indicator.enums;

import com.fasterxml.jackson.annotation.JsonValue;

public enum EntryType {

    /**
     * 數據源類型
     */
    CREATE(0, "新建"),
    LINK(1, "关联");
    private Integer code;

    private String desc;

    EntryType(int code, String desc) {
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
