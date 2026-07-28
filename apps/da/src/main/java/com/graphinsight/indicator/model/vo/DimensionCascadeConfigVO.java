package com.graphinsight.indicator.model.vo;

import io.swagger.annotations.ApiModelProperty;
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
    @ApiModelProperty(value = "层次ID",example = "1",required = true)
    private Integer hierarchyId;


    @NotNull
    @ApiModelProperty(value = "级别信息",required = true)
    private List<LevelVO> levels;

}
