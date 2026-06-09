package com.graphinsight.indicator.model.vo;

import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

import javax.validation.constraints.NotNull;

/**
 * Author: lixiaolong
 * Date: 2022/8/22
 * Desc:
 */
@Data
public class NaturalDimConfigVO {

    @NotNull(message = "指标ID不能为空")
    @ApiModelProperty(value = "指标主键",example = "100")
    private Long measId;

    @NotNull(message = "维度ID不能为空")
    @ApiModelProperty(value = "维度主键",example = "100")
    private Long dimId;

    @NotNull(message = "公共维度维度ID不能为空")
    @ApiModelProperty(value = "维度主键",example = "100")
    private Long hyperDimId;

    @NotNull(message = "模型ID不能为空")
    @ApiModelProperty(value = "模型主键",example = "100")
    private Long modelId;

    private String dimCnName;

}
