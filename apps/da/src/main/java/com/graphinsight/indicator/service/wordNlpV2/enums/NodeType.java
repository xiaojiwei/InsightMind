package com.graphinsight.indicator.service.wordNlpV2.enums;

import java.util.Optional;


public enum NodeType {

    //指标、维度、维度值、运算符、排序、限制
    MEAS("meas", "指标"),
    DIM("dim", "维度"),
    DIM_VALUE("dim-value", "维度值"),
    IN("in", "in"),
    NOTIN("not-in", "not-in"),
    BETEEN("beteen", "between"),
    GREATER_THAN("greater-than", "大于"),
    SMALLER_THAN("smaller-than", "小于"),
    GREATER_THAN_OR_EQUAL("greater-than-or-equal", "大于等于"),
    SMALLER_THAN_OR_EQUAL("smaller-than-or-equal", "小于等于"),
    EQUAL("equal", "="),
    NOT_EQUAL("not-equal", "<>"),
    LIKE("like", "like"),
    LIKE_NO_INCLUDE("like-no-include", "not like"),
    EQUAL_NULL("equal-null", "等于null"),
    EQUAL_NO_NULL("equal-not-null", "不等于null"),
    EQUAL_NULL_CHART("equal-null-chart", "等于null字符"),
    EQUAL_NO_NULL_CHART("equal-not-null-chart", "不等于null字符"),
    IS_NULL("isNull", "为空"),
    ORDER_ASC("order_asc", "升序"),
    ORDER_DESC("order_desc", "降序"),
    limit("limit", "限制");

    NodeType(String code, String desc) {
        this.code = code;
        this.desc = desc;
    }

    private String code;
    private String desc;

    public String getCode() {
        return code;
    }

    public String getDesc() {
        return desc;
    }

    public static boolean isOp(String code) {
        NodeType nodeType = findByCode(code).orElse(null);
        if (nodeType == null) {
            return false;
        }
        switch (nodeType) {
            case IN:
            case NOTIN:
            case BETEEN:
            case GREATER_THAN:
            case SMALLER_THAN:

            case GREATER_THAN_OR_EQUAL:
            case SMALLER_THAN_OR_EQUAL:
            case EQUAL:

            case NOT_EQUAL:
            case LIKE:
            case LIKE_NO_INCLUDE:

            case EQUAL_NULL:
            case EQUAL_NO_NULL:
            case EQUAL_NULL_CHART:

            case EQUAL_NO_NULL_CHART:
            case IS_NULL:

                return true;
            default:
                return false;
        }
    }

    public static Optional<NodeType> findByCode(String name) {
        for (NodeType item : NodeType.values()) {
            if (item.code.equals(name)) {
                return Optional.of(item);
            }
        }

        return Optional.empty();
    }
}
