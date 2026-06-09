package com.graphinsight.indicator.model;

import lombok.Data;

import java.util.HashSet;
import java.util.Set;

/**
 * 查找維度集合
 */
@Data
public class FindDimensionTuple {

    /**
     * 目标维度
     */
    private Dimension targetDimension;

    /**
     * 父维度筛选条件
     */
    private Set<CubeMember> whereParentDimSet = new HashSet<>();

}
