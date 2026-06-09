package com.graphinsight.indicator.enums;

import com.fasterxml.jackson.annotation.JsonValue;

public enum RatioExpType {

    /**
     * 计算百分比
     */
    DIFFPERCENTAGE(0, "差值百分比"),
    DIFF(1, "差值"),
    PERCENTAGE(2, "比值百分数");

    private Integer code;
    private String desc;

    RatioExpType(Integer code, String desc) {
        this.code = code;
        this.desc = desc;
    }

    public static RatioExpType getTypeByCode(Integer code){
        if (code == null){
            return null;
        }
        RatioExpType[] values = RatioExpType.values();
        for (RatioExpType value : values) {
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
