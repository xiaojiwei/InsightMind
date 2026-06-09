package com.graphinsight.indicator.enums;

import com.fasterxml.jackson.annotation.JsonValue;

public enum ExecutorPlatform {

    PRESTO(0, "跨存储MPP计算引擎"),
    DORIS(1, "MPP列存储及计算引擎"),
    MEMORY(2, "内存自拼接计算"),
    MARIO(3, "Table、Sql、文件查询工具引擎"),
    SYNCFILE(4, "异步文件下载引擎");

    private Integer code;
    private String desc;

    ExecutorPlatform(Integer code, String desc) {
        this.code = code;
        this.desc = desc;
    }

    @JsonValue
    public Integer getCode() {
        return code;
    }

    public void setCode(Integer code) {
        this.code = code;
    }

    public String getDesc() {
        return desc;
    }

    public void setDesc(String desc) {
        this.desc = desc;
    }

}
