package com.graphinsight.indicator.model.vo;

import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;

/**
 * Author: lixiaolong
 * Date: 2022/3/1
 * Desc:
 */
@Data
public class CnNameRepeatCheckVO extends BaseVO {

    @NotNull
    @ApiModelProperty(value = "类型 1-指标或者维度 2-模型中文名",required = true)
    private Integer type;

    @NotBlank
    @ApiModelProperty(value = "英文名",required = true)
    private String cnName;
}
