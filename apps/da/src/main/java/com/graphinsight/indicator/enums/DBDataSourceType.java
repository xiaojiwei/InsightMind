package com.graphinsight.indicator.enums;

import com.fasterxml.jackson.annotation.JsonValue;

import java.util.Optional;

public enum DBDataSourceType {

    /**
     * 指标平台
     */
    MYSQL(0, "MYSQL"),
    /**
     * 表类型
     */
    DORIS(1, "DORIS");


    private Integer code;

    private String desc;

    DBDataSourceType(Integer code, String desc) {
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

    public static Optional<DBDataSourceType> findByInt(Integer value) {
        if (value == null) { return Optional.empty(); }
        for (DBDataSourceType item : DBDataSourceType.values()) {
            if (item.code.equals(value)) {
                return Optional.of(item);
            }
        }

        return Optional.empty();
    }

}
