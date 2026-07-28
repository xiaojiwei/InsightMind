package com.graphinsight.indicator.enums;

/**
 * Date: 2022/2/21
 * Desc:
 */
public enum DimFrontType {

    DIM_WITHOUT_TABLE(0,"无维表"),DIM_WITH_TABLE(1,"有维表");

    private Integer code;

    private String desc;

    DimFrontType(Integer code, String desc) {
        this.code = code;
        this.desc = desc;
    }

    public Integer getCode() {
        return code;
    }

    public String getDesc() {
        return desc;
    }
}
