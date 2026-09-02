package com.graphinsight.indicator.openapi.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.LinkedList;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class DimAndValuesDTO {

    private String code;

    private List<String> values = new LinkedList<>();
}
