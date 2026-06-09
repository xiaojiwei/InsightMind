package com.graphinsight.indicator.enums;

import com.fasterxml.jackson.annotation.JsonValue;

public enum SortBy {

    /**
     * 维度排序方式
     */
    DIMENSION(0, "维度"),
    MEASURE(1, "指标"),
    CURRENT_VALUE(2, "本期值"),
    BASE_VALUE(3, "基期值"),
    CONTRIBUTION(4, "贡献度"),
    CONTRIBUTION_RATE(5,"贡献占比");

    private Integer code;
    private String desc;

    SortBy(Integer code, String desc) {
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
