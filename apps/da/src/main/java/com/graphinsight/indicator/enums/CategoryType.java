package com.graphinsight.indicator.enums;

public enum CategoryType {

    /**
     * cell内容类型位置
     */
    DIMENSION(2),
    MEASURE(1),
    MODEL(3);

    private int code;

    CategoryType(int code) {
        this.code = code;
    }

    public int getCode() {
        return code;
    }
}
