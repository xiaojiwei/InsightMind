package com.graphinsight.indicator.openapi.dto;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.LinkedList;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@ApiModel(description = "维度的唯一标识和维值")
public class DimAndValuesDTO {

    @ApiModelProperty(value = "维度的唯一标识")
    private String code;

    @ApiModelProperty(value = "维度的维值列表")
    private List<String> values = new LinkedList<>();
}
