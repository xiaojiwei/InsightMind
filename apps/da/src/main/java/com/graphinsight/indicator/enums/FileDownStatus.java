package com.graphinsight.indicator.enums;

import com.fasterxml.jackson.annotation.JsonValue;

public enum FileDownStatus {

    /**
     * 文件下载状态
     */
    WAIT(0, "等待"),
    RUNING(1, "运行"),
    FAIL(2, "失败"),
    COMPLETE(3, "完成"),
    INVALID(4, "失效");

    private Integer code;

    private String desc;

    @JsonValue
    public Integer getCode() {
        return code;
    }

    FileDownStatus(Integer code, String desc) {
        this.code = code;
        this.desc = desc;
    }

    public String getDesc() {
        return desc;
    }

    public void setDesc(String desc) {
        this.desc = desc;
    }


}
