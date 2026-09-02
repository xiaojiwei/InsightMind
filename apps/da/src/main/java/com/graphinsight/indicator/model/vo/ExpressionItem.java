package com.graphinsight.indicator.model.vo;

import lombok.Data;

import javax.validation.constraints.NotBlank;

/**
 * Date: 2022/2/9
 * Desc:
 */
@Data
public class ExpressionItem {

    @NotBlank(message = "操作类型不能为空")
    private String operatingType;

    /**
     * 操作符，当operatingType为operator时，非空
     */
    private String operator;

    /**
     * 操作数，当operatingType为operand时，非空
     */
    private MeasureBasicInfoVO operand;

    /**
     * 常数，当operationType为constant时，非空
     */
    private Double constant;

}
