package com.graphinsight.indicator.openapi.dto;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@ApiModel(description = "指标信息")
public class MeasureDTO {

    @ApiModelProperty(value = "指标名称",required = true)
    private String name;

    @ApiModelProperty(value = "指标唯一标识",required = true)
    private String code;

    @ApiModelProperty(value = "指标描述",required = true)
    private String description;
}
