package com.graphinsight.indicator.model;

import lombok.Data;

import java.util.Set;

@Data
public class IndicatorTuple {

    /**
     * 维度信息
     */
    private Set<Dimension> dimensionSet;

    /**
     * 指标信息
     */
    private Set<Measure> measureSet;

}
