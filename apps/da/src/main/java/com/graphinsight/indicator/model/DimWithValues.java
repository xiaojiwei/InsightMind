package com.graphinsight.indicator.model;


import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import javax.validation.constraints.NotNull;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class DimWithValues {

    @NotNull(message = "维度code不能为空")
    private String dimensionCode;

    @NotNull(message = "维值不能为空")
    private List<BaseDimValue> values;

    private Integer seq = 1;
}
