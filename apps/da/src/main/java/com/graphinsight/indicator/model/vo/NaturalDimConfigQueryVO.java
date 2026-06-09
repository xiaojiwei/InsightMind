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
public class NaturalDimConfigQueryVO {

    @NotNull(message = "指标ID不能为空")
    @ApiModelProperty(value = "指标主键",example = "100")
    private Long measId;


    @NotNull(message = "模型ID不能为空")
    @ApiModelProperty(value = "模型主键",example = "100")
    private Long modelId;


}
