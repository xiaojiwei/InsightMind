package com.graphinsight.indicator.model.vo;

import com.graphinsight.indicator.enums.DecisionTreeNodeType;
import com.graphinsight.indicator.exception.IndicatorParamNotValidException;

import java.util.Objects;

/**
 * Date: 2022/6/22
 * Desc:
 */
public enum DecisionTreeFrontNodeType {

    MEASURE(0, "指标"),
    OPERATOR(1, "运算符"),
    DIMENSION(2, "维度"); // 预留字段，暂时用不到

    public static final String ADDITION = "+";
    public static final String SUBTRACTION = "-";
    public static final String MULTIPLICATION = "*";
    public static final String DIVISION = "/";

    DecisionTreeFrontNodeType(Integer code, String desc) {
        this.code = code;
        this.desc = desc;
    }

    public Integer getCode() {
        return code;
    }

    public String getDesc() {
        return desc;
    }

    private Integer code;
    private String desc;

    public static DecisionTreeFrontNodeType getType(Integer nodeType){
        if (Objects.isNull(nodeType)) {
            throw IndicatorParamNotValidException.error("参数不能为空");
        }

        DecisionTreeFrontNodeType[] values = DecisionTreeFrontNodeType.values();
        for (DecisionTreeFrontNodeType value : values) {
            if (Objects.equals(value.code,nodeType)){
                return value;
            }
        }
        return null;
    }

    public static DecisionTreeNodeType convert(DecisionTreeFrontNodeType frontNodeType, String operator) {
        if (Objects.isNull(frontNodeType) || Objects.isNull(operator)) {
            throw IndicatorParamNotValidException.error("节点类型或节点内容为空");
        }
        switch (frontNodeType) {
            case MEASURE:
                return DecisionTreeNodeType.MEASURE;
            case OPERATOR:
                if (Objects.equals(ADDITION, operator)) {
                    return DecisionTreeNodeType.ADDITION;
                } else if (Objects.equals(SUBTRACTION, operator)) {
                    return DecisionTreeNodeType.SUBTRACTION;
                } else if (Objects.equals(MULTIPLICATION, operator)) {
                    return DecisionTreeNodeType.MULTIPLICATION;
                } else if (Objects.equals(DIVISION, operator)) {
                    return DecisionTreeNodeType.DIVISION;
                }
                throw IndicatorParamNotValidException.error("节点操作类型不合法");
            case DIMENSION:
                return DecisionTreeNodeType.DIMENSION;
            default:
                throw IndicatorParamNotValidException.error("节点操作类型不合法");
        }

    }
}
