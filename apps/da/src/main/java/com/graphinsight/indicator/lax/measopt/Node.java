package com.graphinsight.indicator.lax.measopt;

import lombok.Data;

import java.math.BigDecimal;

/**
 * Date: 2023/6/5
 * Desc:
 */
@Data
public class Node {
    private String oriText;
    public Node leftNode;
    public Node rightNode;

    public Node() {
    }

    public Node(String oriText) {
        this.oriText = oriText;
    }

    public BigDecimal value(){
        return BigDecimal.valueOf(Double.valueOf(oriText));
    }
}
