package com.graphinsight.indicator.model.vo;

import lombok.Data;

/**
 * Date: 2022/11/23
 * Desc:
 */
@Data
public class DimensionAnalysisGiniQueryVO extends BaseVO {

    /**
     * 指标code
     */
    private String measCode;

    /**
     * 维度code
     */
    private String dimCode;

    /**
     * 本期时间
     */
    private String currentPeriod;

    /**
     * 基期时间
     */
    private String basePeriod;

    /**
     * 空间ID
     */
    private Long spaceId;

    /**
     * 当期值
     */
    private String currentValue;

    /**
     * 本期值
     */
    private String baseValue;
}
