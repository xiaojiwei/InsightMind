package com.graphinsight.indicator.graph;

import lombok.Data;

import java.math.BigDecimal;

/**
 * One materialized reasoning relation plus optional evidence.
 */
@Data
public class ReasoningRelationDTO {

    private String sourceCode;
    private String sourceName;
    private String sourceType;
    private String targetCode;
    private String targetName;
    private String targetType;
    private String relation;
    private String ruleId;
    private BigDecimal confidence;
    private String evidencePath;
}
