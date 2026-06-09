package com.graphinsight.indicator.model.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class GoalQueryVO {

    private Integer spaceId;

    private String measureCode;

    private Integer dimViewType;

    private String dimensionValue;
}
