package com.graphinsight.indicator.enums;

/**
 * Date: 2022/6/14
 * Desc:
 */
public enum DimensionAnalysisTaskStatusType {

    INITIAL(0, "初始化"),
    PROCESSION(1, "查询中"),
    COMPLETED(2, "查询成功"),
    PART_COMPLETED(3, "部分成功"),
    FAILED(4, "查询失败");



    DimensionAnalysisTaskStatusType(Integer code, String desc) {
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
