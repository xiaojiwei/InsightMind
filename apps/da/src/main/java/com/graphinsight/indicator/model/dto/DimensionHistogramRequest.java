package com.graphinsight.indicator.model.dto;

import lombok.Data;

import java.util.Set;

/**
 * Date: 2023/1/4
 * Desc:
 */
@Data
public class DimensionHistogramRequest {
    
    private String code;

    private Set<String> tableNames;

}
