package com.graphinsight.indicator.enums;

import com.fasterxml.jackson.annotation.JsonValue;

/**
 * 对比方式决定贡献度计算的依据
 * 目前不支持目标值对比
 */
public enum DismantlingTreeQueryComparedType {

    /**
     * 周期对比
     */
    CYCLE(0),
    /**
     * 目标值对比
     */
    TARGET_VALUE(1);

    private int code;

    DismantlingTreeQueryComparedType(int code) {
        this.code = code;
    }

    @JsonValue
    public int getCode() {
        return code;
    }
}
