package com.graphinsight.indicator.enums;

import com.fasterxml.jackson.annotation.JsonValue;

import java.util.Optional;

public enum DataOprType {

    /**
     * 明细：当为明细操作时,直接读取数据，无任何分组、聚合操作。
     */
    DETAIL_TABLE_OPERATION(0, "明细操作"),
    /**
     * 聚合：当为聚合操作时，分组、聚合查询。
     */
    AGGREGATION_TABLE_OPERATION(1, "聚合操作");

    private Integer code;

    private String desc;

    DataOprType(Integer code, String desc) {
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

    public static Optional<DataOprType> findByInt(Integer value) {
        if (value == null) { return Optional.empty(); }
        for (DataOprType item : DataOprType.values()) {
            if (item.code.equals(value)) {
                return Optional.of(item);
            }
        }

        return Optional.empty();
    }

}
