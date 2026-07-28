package com.graphinsight.indicator.model.dto;

import lombok.Data;

import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;

/**
 * Date: 2022/11/3
 * Desc:
 */
@Data
public class DismantlingConfigTree {

    /**
     * 主键
     */
    private Long id;

    /**
     * 空间ID
     */
    private Long spaceId;

    /**
     * 决策树名称
     */
    private String name;

    /**
     * 根节点指标code
     */
    private String rootMeasCode;

    /**
     * 是否是默认树
     */
    private Integer isDefault;

    /**
     * 引用的所有指标code
     */
    private Set<String> measCodes = new HashSet<>();

    /**
     * 引用的所有维度code
     */
    private Set<String> dimCodes = new HashSet<>();

    /**
     * 树的配置项(后端用)
     */
    private List<DismantlingConfigTreeFloor> floors = new LinkedList<>();

    /**
     * 树的配置项(前端用)
     */
    private String feConfig;

}
