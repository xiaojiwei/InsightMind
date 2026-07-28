package com.graphinsight.indicator.model.cache;

import lombok.Data;

import java.util.HashSet;
import java.util.Set;

/**
 * Date: 2022/5/12
 * Desc: 空间的上下文信息
 */
@Data
public class SpaceContext {

    /**
     * 空间ID
     */
    private Long id;

    /**
     * 空间名称
     */
    private String name;

    /**
     * 空间下分配的指标分类
     */
    private Set<String> measCategoryIds;

    /**
     * 空间下分配的指标分类
     */
    private Set<Integer> measCategoryIdsWithChildren = new HashSet<>();

    /**
     * 空间下分配的指标
     */
    private Set<Integer> measIdsWithChildren = new HashSet<>();

    /**
     * 空间code
     */
    private String code;
}
