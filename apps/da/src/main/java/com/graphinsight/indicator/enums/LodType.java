package com.graphinsight.indicator.enums;

import com.fasterxml.jackson.annotation.JsonValue;
import org.joda.time.DateTime;
import org.joda.time.format.DateTimeFormat;
import org.joda.time.format.DateTimeFormatter;

import java.util.Optional;

public enum LodType {

    FIXED(0,"FIXED"),
    EXCLUDE(1, "EXCLUDE");

    private Integer code;
    private String desc;

    LodType(Integer code, String name) {
        this.code = code;
        this.desc = name;
    }

    public static LodType build(String name) {
        if (FIXED.getDesc().equalsIgnoreCase(name)) {
            return FIXED;
        } else {
            return EXCLUDE;
        }
    }

    @JsonValue
    public Integer getCode() {
        return code;
    }

    public String getDesc() {
        return desc;
    }

    public void setDesc(String desc) {
        this.desc = desc;
    }

}
