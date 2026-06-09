package com.graphinsight.indicator.lax.measopt;

import java.math.BigDecimal;

/**
 * Author: lixiaolong
 * Date: 2023/6/5
 * Desc:
 */
public class ExprNode extends Node {

    public Operator operator;

    @Override
    public BigDecimal value() {
        if (operator.equals(Operator.ADD)) {
            return leftNode.value().add(rightNode.value());
        } else if (operator.equals(Operator.SUB)) {
            return leftNode.value().subtract(rightNode.value());
        } else if (operator.equals(Operator.MUL)) {
            return leftNode.value().multiply(rightNode.value());
        } else if (operator.equals(Operator.DIV)) {
            return leftNode.value().divide(rightNode.value(), 10, BigDecimal.ROUND_DOWN);
        } else {
            return null;
        }
    }
}
