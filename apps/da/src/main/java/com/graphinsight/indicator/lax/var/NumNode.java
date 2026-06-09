package com.graphinsight.indicator.lax.var;

/**
 * Author: lixiaolong
 * Date: 2023/6/5
 * Desc:
 */
public class NumNode extends Node {
    int value = 0;

    public NumNode(String text){
        super(text);
        value = Integer.parseInt(text);
    }

    @Override
    public Object value() {
        return value;
    }
}
