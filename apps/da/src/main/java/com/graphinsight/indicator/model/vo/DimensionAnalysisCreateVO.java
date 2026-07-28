package com.graphinsight.indicator.model.vo;

import lombok.Data;

import javax.validation.constraints.NotNull;

/**
 * Date: 2022/7/5
 * Desc:
 */
@Data
public class DimensionAnalysisCreateVO extends BaseVO{


    /**
     * 指标code
     */
    @NotNull(message = "指标code不能为空")
    private String measCode;

    /**
     * 维度code
     */
    @NotNull(message = "维度code不能为空")
    private String dimCode;

    /**
     * 本期时间
     */
    @NotNull(message = "本期时间不能为空")
    private String currentDate;

    /**
     * 基期时间
     */
    @NotNull(message = "基期时间不能为空")
    private String baseDate;


    /**
     * 空间ID
     */
    @NotNull(message = "空间ID不能为空")
    private Long spaceId;


}
