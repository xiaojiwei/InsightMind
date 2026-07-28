package com.graphinsight.indicator.model.vo;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * Date: 2022/8/31
 * Desc:
 */
@Data
public class TreeNode<T> {
    private T data;
    private List<TreeNode<T>> children = new ArrayList<>();
}
