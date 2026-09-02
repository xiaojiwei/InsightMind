package com.graphinsight.indicator.openapi.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class MeasureDTO {

    private String name;

    private String code;

    private String description;
}
