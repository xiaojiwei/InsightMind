package com.graphinsight.indicator.model;


import io.swagger.annotations.ApiModelProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import javax.validation.constraints.NotNull;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class DimWithValues {

    @ApiModelProperty(value = "维度code")
    @NotNull(message = "维度code不能为空")
    private String dimensionCode;

    @NotNull(message = "维值不能为空")
    @ApiModelProperty(value = "维值，可多选")
    private List<BaseDimValue> values;

    @ApiModelProperty(value = "排列序号")
    private Integer seq = 1;
}
