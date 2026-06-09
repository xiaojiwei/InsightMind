package com.graphinsight.indicator.enums;

/**
 * Author: lixiaolong
 * Date: 2022/9/1
 * Desc:
 */
public enum WidgetTypeEnum {

    CHART(0, "图表"),
    TAB(1, "Tab"),
    TAB_ITEM(2, "Tab-item"),
    FILTER(3, "筛选器");

    WidgetTypeEnum(Integer code, String desc) {
        this.code = code;
        this.desc = desc;
    }

    private Integer code;
    private String desc;

    public Integer getCode() {
        return code;
    }

    public String getDesc() {
        return desc;
    }
}
