package com.graphinsight.indicator.model.vo;

import lombok.Data;

import java.util.HashSet;
import java.util.Set;

/**
 * @Author: lixiaolong
 * @Description:
 * @Date: 2021/11/26
 */
@Data
public class RelatedCodeSet extends BaseVO {
    private Set<String> measureSet = new HashSet<>();
    private Set<String> dimensionSet = new HashSet<>();
    private Long spaceId;
    private boolean filterWithRelyDimensions;
}
