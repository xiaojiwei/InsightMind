package com.graphinsight.indicator.enums;

/**
 * Author: lixiaolong
 * Date: 2022/6/14
 * Desc:
 */
public enum DimensionAnalysisTaskDetailStatusType {

    INITIAL(0, "未开始"),
    COMPLETED(1, "已完成"),
    FAILED(2, "计算失败");

    DimensionAnalysisTaskDetailStatusType(Integer code, String desc) {
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
