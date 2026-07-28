package com.graphinsight.indicator.model.vo;

import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

import java.util.LinkedList;

/**
 * Date: 2022/3/16
 * Desc:
 */
@Data
public class ComplexMeasureVO {

    @ApiModelProperty(value = "指标应用ID")
    private Integer measAppId;

    @ApiModelProperty(value = "表达式列表")
    public LinkedList<ExpressionItem> expressionItemList;

    @ApiModelProperty(value = "维度筛选列表,创建派生指标的时候传此字段")
    public LinkedList<DimensionFilterCreateVO> dimensionFilterList;

    @ApiModelProperty(value = "指标类型",example = "0-原子指标 1-复合指标 2-派生指标")
    public Integer applyType;

}
