package com.graphinsight.indicator.model.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class GoalDateDimDTO {

    private Integer dimViewType;

    private String dimensionCode;

    private String dimensionCnName;
}
