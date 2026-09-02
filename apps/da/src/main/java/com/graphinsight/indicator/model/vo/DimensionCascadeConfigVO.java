package com.graphinsight.indicator.model.vo;

import lombok.Data;

import javax.validation.constraints.NotNull;
import java.util.List;

/**
 * Date: 2022/1/28
 * Desc:
 */
@Data
public class DimensionCascadeConfigVO extends BaseVO {


    @NotNull
    private Integer hierarchyId;


    @NotNull
    private List<LevelVO> levels;

}
