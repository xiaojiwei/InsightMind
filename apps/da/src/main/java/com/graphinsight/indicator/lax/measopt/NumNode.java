package com.graphinsight.indicator.lax.measopt;

import java.math.BigDecimal;

/**
 * Author: lixiaolong
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
    public BigDecimal value() {
        return value;
    }
}
