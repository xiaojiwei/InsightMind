package com.graphinsight.indicator.model.cache;

import lombok.Data;

import java.util.HashSet;
import java.util.Set;

/**
 * Author: lixiaolong
 * Date: 2022/2/17
 * Desc:
 */
@Data
public class MeasureApplicationDependency {
    private Integer measId;
    private Integer measAppId;

    /**
     * 依赖的指标
     */
    private Set<Integer> dependencyMeasIds = new HashSet<>();

    /**
     * 依赖的维度
     */
    private Set<Integer> dependencyDimIds = new HashSet<>();

    /**
     * 依赖的所有基础指标
     */
    private Set<Integer> dependencyBaseMeasIds = new HashSet<>();

    /**
     * 依赖的所有基础维度
     */
    private Set<Integer> dependencyBaseDimIds = new HashSet<>();
}
