package com.graphinsight.indicator.model.vo;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * Date: 2022/3/9
 * Desc:
 */
@Data
public class CategoryTreeNode<T> {

    private T data;

    private List<CategoryTreeNode<T>> children = new ArrayList<>();
}
