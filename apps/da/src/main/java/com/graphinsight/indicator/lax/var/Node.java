package com.graphinsight.indicator.lax.var;

import lombok.Data;

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

    public Object value(){
        return oriText;
    }
}
