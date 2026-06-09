package com.graphinsight.indicator.enums;

import com.fasterxml.jackson.annotation.JsonValue;

public enum CacheStrategy {

    DEFAULT(0,"默认使用缓存"),
    QUERY_UPDATE(1,"返回缓存结果，异步更新缓存内容"),
    DELETE(2, "仅删除缓存"),
    OVERWRITE(3,"强刷并更新缓存");

    private Integer code;
    private String desc;

    @JsonValue
    public Integer getCode() {
        return code;
    }

    CacheStrategy(Integer code, String desc) {
        this.code = code;
        this.desc = desc;
    }

    public String getDesc() {
        return desc;
    }

    public void setDesc(String desc) {
        this.desc = desc;
    }

}
