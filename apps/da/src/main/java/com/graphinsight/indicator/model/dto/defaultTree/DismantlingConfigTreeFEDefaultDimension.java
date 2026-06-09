package com.graphinsight.indicator.model.dto.defaultTree;

import java.util.Set;

import lombok.Data;

@Data
public class DismantlingConfigTreeFEDefaultDimension {
    private String cnName;
    private String code;
    private Set<String> values;
}
