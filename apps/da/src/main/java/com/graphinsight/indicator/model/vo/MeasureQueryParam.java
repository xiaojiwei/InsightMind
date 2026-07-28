package com.graphinsight.indicator.model.vo;

import lombok.Data;

import java.util.Set;

/**
 * Date: 2022/10/18
 * Desc:
 */
@Data
public class MeasureQueryParam {
    private Set<Integer> categoryIds;
    private Long spaceId;
}
