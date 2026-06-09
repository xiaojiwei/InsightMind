package com.graphinsight.indicator.model.vo;

import lombok.Data;

import javax.validation.constraints.NotNull;

/**
 * Author: lixiaolong
 * Date: 2022/6/20
 * Desc:
 */
@Data
public class DecisionTreeQueryVO {

    @NotNull(message = "ID不能为空")
    private Long id;

    @NotNull(message = "空间ID不能为空")
    private Long spaceId;

    /**
     * 维度Code
     */
    private String dimCode;

    /**
     * 本期
     */
    private String currentDate;

    /**
     * 基期
     */
    private String baseDate;

}
