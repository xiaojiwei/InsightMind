package com.graphinsight.indicator.enums;

import java.util.Objects;

/**
 * Date: 2022/6/15
 * Desc:
 */
public enum ContributionCalculationType {

    ADDITION(2, "加法"),
    SUBTRACTION(3,"减法"),
    MULTIPLICATION(4,"乘法"),
    DIVISION(5,"除法"),
    TWO_FACTOR(6,"双因素"),
    DEFAULT(100,"默认策略");

    private Integer code;
    private String desc;

    ContributionCalculationType(Integer code, String desc) {
        this.code = code;
        this.desc = desc;
    }

    public Integer getCode() {
        return code;
    }

    public String getDesc() {
        return desc;
    }


    public static ContributionCalculationType getByCode(Integer code){
        if (Objects.isNull(code)){
            return DEFAULT;
        }
        for (ContributionCalculationType value : ContributionCalculationType.values()) {
            if (value.code.intValue() == code.intValue()){
                return value;
            }
        }
        return DEFAULT;
    }

}
