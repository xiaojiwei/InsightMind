package com.graphinsight.indicator.model.dto;

import lombok.Data;

import java.util.Set;

/**
 * Date: 2022/5/16
 * Desc:
 */
@Data
public class AuthDimensionBloodCheckResult {

    private String authDimensionCode;

    private Set<String> dimensionCodes;

    private Set<String> measureCodes;

    private boolean hasBlood;

}
