package com.graphinsight.indicator.enums;

/**
 * Date: 2022/3/1
 * Desc:
 */
public enum  CheckNameType {
    FIELD(1,"指标或者维度"),TABLE(2,"模型");


    private int code;

    private String desc;

    CheckNameType(int code, String desc) {
        this.code = code;
        this.desc = desc;
    }

    public int getCode() {
        return code;
    }

    public String getDesc() {
        return desc;
    }
}
