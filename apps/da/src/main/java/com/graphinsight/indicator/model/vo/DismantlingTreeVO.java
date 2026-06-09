package com.graphinsight.indicator.model.vo;

import lombok.Data;

import java.util.HashSet;
import java.util.Set;

/**
 * Author: lixiaolong
 * Date: 2022/11/4
 * Desc:
 */
@Data
public class DismantlingTreeVO {

    private Long id;

    private String treeName;

    private Long spaceId;

    private Boolean isDefault;

    private DismantlingTreeNode root;

    private Set<String> measCodes = new HashSet<>();

    private Set<String> dimCodes = new HashSet<>();
}
