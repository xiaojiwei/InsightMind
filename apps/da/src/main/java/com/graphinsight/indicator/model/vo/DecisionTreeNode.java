package com.graphinsight.indicator.model.vo;

import lombok.Data;

import java.util.List;

/**
 * Date: 2022/6/20
 * Desc:
 */
@Data
public class DecisionTreeNode {

    private DecisionTreeNodeData nodeData;

    private String parentCode;

    private Integer treeLevelSeq;

    private Integer nodeType;

    private boolean isRatio;

    private List<DecisionTreeNode> children;

}
