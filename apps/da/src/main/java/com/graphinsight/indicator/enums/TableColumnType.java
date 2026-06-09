package com.graphinsight.indicator.enums;

import com.fasterxml.jackson.annotation.JsonValue;

/**
 * Author: lixiaolong
 * Date: 2022/2/22
 * Desc: 模型字段类型
 */
public enum TableColumnType {

    MEASURE(1, "指标"),
    DIMENSION(2, "维度");
    private Integer code;

    private String desc;

    TableColumnType(int code, String desc) {
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
