package com.graphinsight.indicator.model.vo;

import lombok.Data;

import java.util.List;

/**
 * Date: 2022/1/28
 * Desc:
 */
@Data
public class CascadeVO {

    private Integer hierarchyId;

    private List<LevelVO> levels;
}
