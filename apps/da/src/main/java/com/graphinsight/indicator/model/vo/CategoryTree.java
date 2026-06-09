package com.graphinsight.indicator.model.vo;

import lombok.Data;

import java.util.List;

/**
 * @Author: lixiaolong
 * @Description:
 * @Date: 2021/11/30
 */
@Data
public class CategoryTree<T> extends BaseVO{
    private List<T> data;
    private Integer id;
    private String name;
    private Integer parentId;
    private Integer sequence;
    private List<CategoryTree> children;
}
