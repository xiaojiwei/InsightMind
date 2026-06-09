package com.graphinsight.indicator.openapi.dto;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@ApiModel(description = "维度信息")
public class DimensionDTO {

    @ApiModelProperty(value = "维度名称",required = true)
    private String name;

    @ApiModelProperty(value = "维度唯一标识",required = true)
    private String code;

    @ApiModelProperty(value = "维度描述",required = true)
    private String description;
}
