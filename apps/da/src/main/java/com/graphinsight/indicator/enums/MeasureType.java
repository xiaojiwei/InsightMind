package com.graphinsight.indicator.enums;

import com.fasterxml.jackson.annotation.JsonValue;

import java.util.Objects;

public enum MeasureType {

    /**
     * 指标类型，对应不同的表达式
     */
    ORIGIN(0, "原生指标"),
    DERIVED(1, "衍生指标|复合指标"),
    EXTENDED(2, "派生指标"),
    GROUP(3, "指标分组");

    private Integer code;
    private String desc;

    MeasureType(Integer code, String desc) {
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

    public void setCode(Integer code) {
        this.code = code;
    }

    public static MeasureType getTypeByCode(Integer code){
        MeasureType[] values = MeasureType.values();

        for (MeasureType value : values) {
            if (Objects.equals(value.getCode(),code)){
                return value;
            }
        }
        return null;
    }

}
