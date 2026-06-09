package com.graphinsight.indicator.openapi.dto;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.HashSet;
import java.util.Set;


@Data
@AllArgsConstructor
@NoArgsConstructor
@ApiModel(description = "指标的唯一标识集合和维度的唯一标识集合")
public class RelationCodeDTO {

    @ApiModelProperty(value = "指标的唯一标识集合",required = true)
    private Set<String> measureSet = new HashSet<>();

    @ApiModelProperty(value = "维度的唯一标识集合",required = true)
    private Set<String> dimensionSet = new HashSet<>();
}
