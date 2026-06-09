package com.graphinsight.indicator.enums;

import com.fasterxml.jackson.annotation.JsonValue;

/**
 * Author: lixiaolong
 * Date: 2022/2/10
 * Desc:
 */
public enum SceneType {
    MEASURE("measure","指标"),
    MDM("mdm","主数据"),
    ;

    private String code;
    private String desc;

    SceneType(String code, String 指标) {
        this.code = code;
    }

    @JsonValue
    public String getCode() {
        return code;
    }

}
