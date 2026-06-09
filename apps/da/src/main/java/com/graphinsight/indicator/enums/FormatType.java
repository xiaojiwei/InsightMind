package com.graphinsight.indicator.enums;

import com.fasterxml.jackson.annotation.JsonValue;

public enum FormatType {

    /**
     * 显示格式
     */
    DEFAULT(0, "默认不做数据格式处理"),
    DECIMAL(1, "自定义小数位"),
    DECIMAL1(2, "带1位小数"),
    DECIMAL2(3, "带2位小数"),
    INTEGER(4, "整数"),
    PERCENT(5, "自定义小数百分比"),
    PERCENT1(6, "1位百分比"),
    PERCENT2(7, "2位百分比"),
    CHARACTER(8, "字符"),
    THOUSANDTH(9, "千分位"),
    MILLION(10, "百万"),
    ;

    private Integer code;
    private String desc;

    FormatType(Integer code, String desc) {
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

}
