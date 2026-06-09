package com.graphinsight.indicator.enums;

import com.fasterxml.jackson.annotation.JsonValue;

public enum SortType {

    /**
     * 维度排序方式
     */
    DEFAULT(0, "默认"),
    DESC(1, "倒序"),
    ASC(2, "正序");

    private Integer code;
    private String desc;

    SortType(Integer code, String desc) {
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

    public void setDesc(String desc) {
        this.desc = desc;
    }

}
