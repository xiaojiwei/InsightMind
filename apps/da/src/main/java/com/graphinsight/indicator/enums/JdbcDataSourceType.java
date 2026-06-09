package com.graphinsight.indicator.enums;

import com.fasterxml.jackson.annotation.JsonValue;

import java.util.Optional;

public enum JdbcDataSourceType {

    /**
     * 指标平台
     */
    DORIS(0, "doris"),
    /**
     * 表类型
     */
    MYSQL(1, "mysql");

    private Integer code;

    private String desc;

    JdbcDataSourceType(Integer code, String desc) {
        this.code = code;
        this.desc = desc;
    }

    public Integer getCode() {
        return code;
    }

    public void setCode(Integer code) {
        this.code = code;
    }

    @JsonValue
    public String getDesc() {
        return desc;
    }

    public void setDesc(String desc) {
        this.desc = desc;
    }

    public static Optional<JdbcDataSourceType> findByInt(Integer value) {
        if (value == null) { return Optional.empty(); }
        for (JdbcDataSourceType item : JdbcDataSourceType.values()) {
            if (item.code.equals(value)) {
                return Optional.of(item);
            }
        }

        return Optional.empty();
    }

}
