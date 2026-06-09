package com.graphinsight.indicator.enums;

import com.fasterxml.jackson.annotation.JsonValue;

import java.util.Objects;

public enum RatioType {

    /**
     * 同环比类型
     */
    DEFAULT(0, "无"),
    MONTHONMONTH(1, "环比"),
    WEEKMOM(2, "周同比"),
    MONTHMOM(3, "月环比"),
    YEARYEMOM(4, "年同比"),
    FIEXED(5, "固定日期"),
    CUSTOMIZE(6, "自定义日期");

    private Integer code;
    private String desc;

    RatioType(Integer code, String desc) {
        this.code = code;
        this.desc = desc;
    }

    public static RatioType getTypeByCode(Integer code){
        if (code == null){
            return null;
        }
        RatioType[] values = RatioType.values();
        for (RatioType value : values) {
            if (value.code.intValue() == code.intValue()){
                return value;
            }
        }
        return null;
    }

    public static RatioType getTypeByName(String descName){
        if (descName == null){
            return null;
        }
        RatioType[] values = RatioType.values();
        for (RatioType value : values) {
            if (Objects.equals(value.desc, descName)){
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
