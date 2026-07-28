package com.graphinsight.indicator.enums;

import com.fasterxml.jackson.annotation.JsonValue;

/**
 * @Description: 事实表类型
 * @Date: 2021/11/18
 */
public enum FactTableType {
    /**
     * 數據源類型
     */
    AGG(0, "聚合表"),
    DETAIL(1, "明细表");
    private Integer code;

    private String desc;

    FactTableType(int code, String desc) {
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
