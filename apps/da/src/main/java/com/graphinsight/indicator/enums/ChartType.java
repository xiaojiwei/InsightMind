package com.graphinsight.indicator.enums;

import com.fasterxml.jackson.annotation.JsonValue;

/**
 * 图表类型
 */
public enum ChartType {
    /**
     * 图类型
     */
    TABLE(0, "表格"),
    LINE(1, "折线图"),
    HIST(2, "柱状图"),
    FUNNEL(3, "漏斗图"),
    CARD(4, "数据卡"),
    PIE(5, "饼图"),
    COMBINE(6, "组合图"),
    SYNCFILE(7, "异步文件"),
    PIVOT(8, "透视交叉表"),
    MEMORY(9, "内存");


    private Integer code;
    private String name;

    ChartType(Integer code, String name) {
        this.code = code;
        this.name = name;
    }

    @JsonValue
    public Integer getCode() {
        return code;
    }

    public void setCode(Integer code) {
        this.code = code;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public boolean isSingleDataRow() {
        return CARD.equals(this) || FUNNEL.equals(this) || PIE.equals(this);
    }

}
