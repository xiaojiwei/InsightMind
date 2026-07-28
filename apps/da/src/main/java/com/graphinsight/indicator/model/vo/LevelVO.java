package com.graphinsight.indicator.model.vo;

import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

import javax.validation.constraints.NotNull;

/**
 * Date: 2022/1/28
 * Desc:
 */
@Data
public class LevelVO {

    @NotNull(message = "维度ID不能为空")
    @ApiModelProperty(value = "维度ID",example = "1")
    private Integer dimId;

    @NotNull(message = "维度中文名不能为空")
    @ApiModelProperty(value = "维度中文名")
    private String cnName;

}
