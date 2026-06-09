package com.graphinsight.indicator.lax.ifelse.iffunction;

import java.math.BigDecimal;

/**
 * Author: lixiaolong
 * Date: 2023/6/6
 * Desc:
 */
public class Node {
    private String oriText;
    public Node leftNode;
    public Node rightNode;

    public Node() {
    }

    public Node(String oriText) {
        this.oriText = oriText;
    }

    public BigDecimal numberic(){
        return BigDecimal.valueOf(Double.valueOf(oriText));
    }

    public Boolean condition(){
        return Boolean.valueOf(oriText);
    }
}
