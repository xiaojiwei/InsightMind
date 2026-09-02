package com.graphinsight.indicator.openapi.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.HashSet;
import java.util.Set;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class RelationCodeVO {

    private Set<String> measureSet = new HashSet<>();

    private Set<String> dimensionSet = new HashSet<>();
}
