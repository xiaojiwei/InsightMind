package com.graphinsight.indicator.enums;

import com.fasterxml.jackson.annotation.JsonValue;

public enum FolderDataSourceType {
    /**
     * cell内容类型位置
     */
    FOLDER(0, "folder"),
    DATA_SOURCE(1,"dataSource");

    private Integer code;
    private String desc;

    FolderDataSourceType(Integer code, String desc) {
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
