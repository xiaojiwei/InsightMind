package com.graphinsight.indicator.model.dto;

import lombok.Data;

/**
 * Date: 2022/8/4
 * Desc:
 */
@Data
public class RatioMeasureDismanling {

    /**
     * 分子指标Code
     */
    private String molecularCode;

    /**
     * 分母指标Code
     */
    private String denominatorCode;

    /**
     * 是否可进行双因素拆解计算
     */
    private boolean computable = false;

}
