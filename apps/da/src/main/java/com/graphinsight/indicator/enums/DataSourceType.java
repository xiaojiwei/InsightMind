package com.graphinsight.indicator.enums;

import com.fasterxml.jackson.annotation.JsonValue;

import java.util.Optional;

public enum DataSourceType {

    /**
     * 指标平台
     */
    INDICATOR(0, "指标平台"),
    /**
     * SQL类型
     */
    SQL(2, "sql类型"),
    /**
     * 表类型
     */
    TABLE(1, "表类型"),

    /**
     * 文件类型
     */
    File(3, "文件类型");

    private Integer code;

    private String desc;

    DataSourceType(Integer code, String desc) {
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

    public static Optional<DataSourceType> findByInt(Integer value) {
        if (value == null) { return Optional.empty(); }
        for (DataSourceType item : DataSourceType.values()) {
            if (item.code.equals(value)) {
                return Optional.of(item);
            }
        }

        return Optional.empty();
    }

}
