package com.graphinsight.indicator.enums;

import com.fasterxml.jackson.annotation.JsonValue;

public enum RatioOperaType {

    /**
     * 维度排序方式
     */
    BEFORE(0, "前"),
    AFTER(1, "后");

    private Integer code;
    private String desc;

    RatioOperaType(Integer code, String desc) {
        this.code = code;
        this.desc = desc;
    }

    public static RatioOperaType getTypeByCode(Integer code){
        if (code == null){
            return null;
        }
        RatioOperaType[] values = RatioOperaType.values();
        for (RatioOperaType value : values) {
            if (value.code.intValue() == code.intValue()){
                return value;
            }
        }
        return null;
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
