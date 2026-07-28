package com.graphinsight.indicator.lax.var;

/**
 * Date: 2023/6/5
 * Desc:
 */
public class ExprNode extends Node {

    public Operator operator;

    @Override
    public Object value() {
        if (operator.equals(Operator.ADD)) {
            return (int)(leftNode.value()) + ((int)rightNode.value());
        } else if (operator.equals(Operator.SUB)) {
            return (int)(leftNode.value()) - ((int)rightNode.value());
        } else if (operator.equals(Operator.MUL)) {
            return (int)(leftNode.value()) * ((int)rightNode.value());
        } else if (operator.equals(Operator.DIV)) {
            return (int)(leftNode.value()) / ((int)rightNode.value());
        } else {
            return null;
        }
    }
}
