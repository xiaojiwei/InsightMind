package com.graphinsight.indicator.model.vo;

import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

import javax.validation.constraints.NotBlank;

/**
 * Author: lixiaolong
 * Date: 2022/3/1
 * Desc:
 */
@Data
public class NameRepeatCheckVO extends BaseVO {

    @NotBlank
    @ApiModelProperty(value = "名称",required = true)
    private String name;
}
