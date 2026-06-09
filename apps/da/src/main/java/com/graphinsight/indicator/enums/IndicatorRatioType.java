package com.graphinsight.indicator.enums;

import com.fasterxml.jackson.annotation.JsonValue;

public enum IndicatorRatioType {

    /**
     * 维度排序方式
     */
    FIX_VALUE(0, "原始值"),
    MONTHONMONTH(1, "环比"),
    WEEKMOM(2, "周环比"),
    MONTHMOM(3, "月环比"),
    YEARYEMOM(4, "年同比");

    private Integer code;
    private String desc;

    IndicatorRatioType(Integer code, String desc) {
        this.code = code;
        this.desc = desc;
    }

    public static IndicatorRatioType getTypeByCode(Integer code) {
        if (code == null) {
            return null;
        }
        IndicatorRatioType[] values = IndicatorRatioType.values();
        for (IndicatorRatioType value : values) {
            if (value.code.intValue() == code.intValue()) {
                return value;
            }
        }
        return null;
    }


    public static RatioType getRatioTypeByCode(Integer code) {
        if (code == null) {
            return null;
        }
        IndicatorRatioType[] values = IndicatorRatioType.values();
        for (IndicatorRatioType value : values) {
            if (value.code.intValue() == code.intValue()) {
                switch (value) {
                    case FIX_VALUE:
                        return null;
                    case MONTHONMONTH:
                        return RatioType.MONTHONMONTH;
                    case WEEKMOM:
                        return RatioType.WEEKMOM;
                    case MONTHMOM:
                        return RatioType.MONTHMOM;
                    case YEARYEMOM:
                        return RatioType.YEARYEMOM;
                }
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
