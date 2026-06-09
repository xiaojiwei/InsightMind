package com.graphinsight.indicator.enums;

import com.graphinsight.indicator.exception.IndicatorParamNotValidException;
import com.graphinsight.indicator.model.vo.DecisionTreeFrontNodeType;

import java.util.Objects;

/**
 * Author: lixiaolong
 * Date: 2022/6/14
 * Desc:
 */
public enum DecisionTreeNodeType {

    MEASURE(0, "指标"),
    ADDITION(1, "加法"),
    SUBTRACTION(2, "减法"),
    MULTIPLICATION(3, "乘法"),
    DIVISION(4, "除法"),
    CONSTANT(5, "常数"),
    DIMENSION(6, "维度"); // 预留字段，暂时用不到


    DecisionTreeNodeType(Integer code, String desc) {
        this.code = code;
        this.desc = desc;
    }

    private Integer code;
    private String desc;

    public Integer getCode() {
        return code;
    }

    public String getDesc() {
        return desc;
    }

    public static DecisionTreeFrontNodeType convert(DecisionTreeNodeType type) {
        if (Objects.isNull(type)) {
            throw IndicatorParamNotValidException.error("节点类型为空");
        }
        switch (type) {
            case MEASURE:
                return DecisionTreeFrontNodeType.MEASURE;
            case ADDITION:
            case SUBTRACTION:
            case MULTIPLICATION:
            case DIVISION:
                return DecisionTreeFrontNodeType.OPERATOR;
            case DIMENSION:
                return DecisionTreeFrontNodeType.DIMENSION;
            default:
                throw IndicatorParamNotValidException.error("节点操作类型不合法");
        }
    }

    /**
     * 判断类型是否是加减乘除运算符
     *
     * @param nodeType
     * @return
     */
    public static DecisionTreeNodeType getType(Integer nodeType) {
        if (Objects.isNull(nodeType)) {
            throw IndicatorParamNotValidException.error("参数不能为空");
        }

        DecisionTreeNodeType[] values = DecisionTreeNodeType.values();
        for (DecisionTreeNodeType value : values) {
            if (Objects.equals(value.code, nodeType)) {
                return value;
            }
        }
        return null;
    }

    /**
     * 判断类型是否是加减乘除运算符
     *
     * @return
     */
    public static DecisionTreeNodeType getType(String symbol) {
        if (Objects.isNull(symbol)) {
            throw IndicatorParamNotValidException.error("参数不能为空");
        }

        switch (symbol){
            case "+":
                return ADDITION;
            case "-":
                return SUBTRACTION;
            case "*":
                return MULTIPLICATION;
            case "/":
                return DIVISION;
            default:
                return null;
        }
    }


    /**
     * 判断类型是否是加减乘除运算符
     *
     * @param nodeType
     * @return
     */
    public static boolean isOperator(DecisionTreeNodeType nodeType) {
        if (Objects.isNull(nodeType)) {
            throw IndicatorParamNotValidException.error("参数不能为空");
        }
        return nodeType.equals(ADDITION) ||
                nodeType.equals(SUBTRACTION) ||
                nodeType.equals(MULTIPLICATION) ||
                nodeType.equals(DIVISION);

    }

    public static boolean supportType(DecisionTreeNodeType nodeType) {
        if (Objects.isNull(nodeType)) {
            return false;
        }
        if (Objects.equals(DIMENSION, nodeType)) {
            return false;
        }
        DecisionTreeNodeType[] values = DecisionTreeNodeType.values();
        for (DecisionTreeNodeType value : values) {
            if (Objects.equals(value, nodeType)) {
                return true;
            }
        }
        return false;
    }
}
