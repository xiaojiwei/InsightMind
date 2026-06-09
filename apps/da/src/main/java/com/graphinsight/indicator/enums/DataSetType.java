package com.graphinsight.indicator.enums;

import com.fasterxml.jackson.annotation.JsonValue;

/**
 * 数据类型
 */
public enum DataSetType {


    LIST_NODE(0, "明细集"),
    /**
     * 分页+同环比
     */
    TABLE(1, "分页表"),

    /**
     * 同环比+单时间点
     */
    CARD(2, "同环比卡片"),

    /**
     * 无同环比和分页
     */
    LIST(3, "无分页数据列表"),

    SQL(4, "只有SQL没有数据"),

    COUNT(5, "总数"),

    SYNCFILE(6, "异步下载文件"),

    PIVOT(7, "透视表"),

    MEASURE_TABLE_LIST(8, "指标明细分页"),

    MEASURE_LIST(9, "指标明细");

    ;

    private Integer code;

    private String desc;

    DataSetType(Integer code, String desc) {
        this.code = code;
        this.desc = desc;
    }

    @JsonValue
    public Integer getCode() {
        return code;
    }

    public void setCode(Integer code) {
        this.code = code;
    }

    public String getDesc() {
        return desc;
    }

    public void setDesc(String desc) {
        this.desc = desc;
    }

}
