package com.graphinsight.indicator.enums;

import com.fasterxml.jackson.annotation.JsonValue;

/**
 * Date: 2022/11/3
 * Desc: 节点的类型
 */
public enum OperatorType {
    ADDITION(0, "加法"),
    SUBTRACTION(1, "减法"),
    MULTIPLICATION(2, "乘法"),
    DIVISION(3, "除法"),
    EMPTY(4, "空"),// 比率型指标的维度拆解，没有运算符，采用双因素拆解
    PLACEHOLDER(5, "占位符运算符 无任何意义");

    private Integer code;
    private String desc;

    @JsonValue
    public Integer getCode() {
        return code;
    }

    public String getDesc() {
        return desc;
    }

    OperatorType(Integer code, String desc) {
        this.code = code;
        this.desc = desc;
    }
}
