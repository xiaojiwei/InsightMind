package com.graphinsight.indicator.enums;

public enum CellType {
    /**
     * cell内容类型位置
     */
    DIMENSION("0"),
    MEASURE("1"),
    MEASURE_GROUP("2"),
    MEASURE_VALUE("3");

    private String desc;

    CellType(String desc) {
        this.desc = desc;
    }

    public String getDesc() {
        return desc;
    }

    public void setDesc(String desc) {
        this.desc = desc;
    }

}
