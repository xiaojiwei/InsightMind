package com.graphinsight.indicator.model;

import lombok.Data;

import java.util.LinkedList;
import java.util.List;

/**
 * 分组列值，用于衍生维度所用
 */
@Data
public class GroupColumn extends BaseModel {

    /**
     * 新的维度分组名称
     */
    private String name;

    /**
     * 维度分组的过滤条件
     * 默认都应以"or”方式
     */
    private List<Filter> filterList = new LinkedList<Filter>();

}
