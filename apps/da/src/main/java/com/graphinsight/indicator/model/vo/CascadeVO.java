package com.graphinsight.indicator.model.vo;

import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

import java.util.List;

/**
 * Date: 2022/1/28
 * Desc:
 */
@Data
public class CascadeVO {

    @ApiModelProperty(value = "层次ID")
    private Integer hierarchyId;

    @ApiModelProperty(value = "级别信息")
    private List<LevelVO> levels;
}
