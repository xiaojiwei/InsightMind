package com.graphinsight.indicator.model.vo;

import lombok.Data;

import java.util.HashSet;
import java.util.Set;

/**
 * @Description:
 * @Date: 2021/11/26
 */
@Data
public class RelatedSet extends BaseVO {
    private Set<Integer> measureSet = new HashSet<>();
    private Set<Integer> dimensionSet = new HashSet<>();
    private boolean filterWithRelyDimensions;
}
