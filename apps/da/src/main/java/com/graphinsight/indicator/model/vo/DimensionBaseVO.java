package com.graphinsight.indicator.model.vo;

import lombok.Data;

import java.util.List;

/**
 * Date: 2022/3/7
 * Desc:
 */
@Data
public class DimensionBaseVO extends  BaseInfo{
    private List<DimensionBaseVO> cascadeDimensions;
    private Integer hierarchyId;
    private Integer sequence;
    private String offlineReason;
}
