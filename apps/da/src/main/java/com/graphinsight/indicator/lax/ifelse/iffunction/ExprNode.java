package com.graphinsight.indicator.lax.ifelse.iffunction;


import com.graphinsight.indicator.lax.measopt.Operator;

import java.math.BigDecimal;

/**
 * Date: 2023/6/5
 * Desc:
 */
public class ExprNode extends Node {

    public Operator operator;

    @Override
    public BigDecimal numberic() {
        if (operator.equals(Operator.ADD)) {
            return leftNode.numberic().add(rightNode.numberic());
        } else if (operator.equals(Operator.SUB)) {
            return leftNode.numberic().subtract(rightNode.numberic());
        } else if (operator.equals(Operator.MUL)) {
            return leftNode.numberic().multiply(rightNode.numberic());
        } else if (operator.equals(Operator.DIV)) {
            return leftNode.numberic().divide(rightNode.numberic(), 10, BigDecimal.ROUND_DOWN);
        } else {
            return null;
        }
    }
}
