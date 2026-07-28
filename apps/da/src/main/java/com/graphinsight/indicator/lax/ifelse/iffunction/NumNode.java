package com.graphinsight.indicator.lax.ifelse.iffunction;


import java.math.BigDecimal;

/**
 * Date: 2023/6/5
 * Desc:
 */
public class NumNode extends Node {
    BigDecimal value;

    public NumNode(String text){
        super(text);
        value = BigDecimal.valueOf(Double.valueOf(text));
    }

    @Override
    public BigDecimal numberic() {
        return value;
    }
}
