package com.graphinsight.indicator.enums;

import com.fasterxml.jackson.annotation.JsonValue;

/**
 * 排序范围交叉表使用
 */
public enum SortScope {

    /**
     * 排序范围
     */
    ALL(0, "全局"),
    GROUP(1, "组内");

    private Integer code;
    private String desc;

    SortScope(Integer code, String desc) {
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
