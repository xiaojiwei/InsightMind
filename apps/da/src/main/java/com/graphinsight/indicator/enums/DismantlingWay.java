package com.graphinsight.indicator.enums;

import com.fasterxml.jackson.annotation.JsonValue;

/**
 * 拆解方式决定着查静态数据还是查基期、本期数据
 */
public enum DismantlingWay {

    /**
     * 静态拆解
     */
    STATIC(0),
    /**
     * 动态拆解
     */
    DYNAMIC(1);

    private int code;

    DismantlingWay(int code) {
        this.code = code;
    }

    @JsonValue
    public int getCode() {
        return code;
    }
}
