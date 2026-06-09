package com.graphinsight.indicator.enums;

import com.fasterxml.jackson.annotation.JsonValue;

/**
 * Author: lixiaolong
 * Date: 2022/2/10
 * Desc:
 */
public enum YesNoType {
    NO(0),
    YES(1);

    Integer code;

    YesNoType(Integer code) {
        this.code = code;
    }

    @JsonValue
    public int getCode() {
        return code;
    }

}
