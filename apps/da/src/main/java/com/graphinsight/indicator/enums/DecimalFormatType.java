package com.graphinsight.indicator.enums;

import com.fasterxml.jackson.annotation.JsonValue;

public enum DecimalFormatType {

    /**
     * 显示格式
     */
    DEFAULT(0, "默认不做数据格式处理"),
    MYRIAD(1, "万"),
    HUNDRED_MILLION(2, "亿"),
    THOUSAND(3, "K"),
    MILLION(4, "M"),
    BILLION(5, "G"), // 十亿
    ;

    private Integer code;
    private String desc;

    DecimalFormatType(Integer code, String desc) {
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


    public static DecimalFormatType findNullableByCode(Integer code) {
        for (DecimalFormatType item : DecimalFormatType.values()) {
            if (item.code.equals(code)) {
                return item;
            }
        }

        return DecimalFormatType.DEFAULT;
    }
}
