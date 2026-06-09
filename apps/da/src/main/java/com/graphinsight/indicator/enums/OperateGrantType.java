package com.graphinsight.indicator.enums;

import com.fasterxml.jackson.annotation.JsonValue;

import java.util.Optional;

public enum OperateGrantType {

    /**
     * 维度列精确授权
     */
    EXECT(0, "维度列精确授权"),

    /**
     * 运营架构授权
     */
    ORG(1, "按照组织授权,包含orgCode及其所有子code");

    private Integer code;

    private String desc;

    OperateGrantType(Integer code, String desc) {
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

    public static Optional<OperateGrantType> findByInt(Integer value) {
        if (value == null) { return Optional.empty(); }
        for (OperateGrantType item : OperateGrantType.values()) {
            if (item.code.equals(value)) {
                return Optional.of(item);
            }
        }

        return Optional.empty();
    }

}
