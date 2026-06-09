package com.graphinsight.indicator.model;

import io.swagger.annotations.ApiModelProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import javax.validation.constraints.NotNull;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class BaseDimValue {
    @NotNull(message = "维值id不能为空")
    @ApiModelProperty(value = "维值id")
    private String id;

    @NotNull(message = "维值data不能为空")
    @ApiModelProperty(value = "维值data")
    private String data;
}
