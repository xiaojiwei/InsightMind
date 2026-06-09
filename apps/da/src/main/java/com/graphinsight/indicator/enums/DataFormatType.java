package com.graphinsight.indicator.enums;

import com.fasterxml.jackson.annotation.JsonValue;

public enum DataFormatType {

    /**
     * 显示格式
     */
    DEFAULT(0,"number"),
    MYRIAD(1, "percentage"),// 十亿
    ;

    private Integer code;
    private String desc;

    DataFormatType(Integer code, String desc) {
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

    public static DataFormatType findNullableByString(String name) {
        for (DataFormatType item : DataFormatType.values()) {
            if (item.desc.equals(name)) {
                return item;
            }
        }

        return DataFormatType.DEFAULT;
    }

}
