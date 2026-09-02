package com.graphinsight.indicator.model.vo;

import lombok.Data;

import java.util.LinkedList;

/**
 * Date: 2022/3/16
 * Desc:
 */
@Data
public class ComplexMeasureVO {

    private Integer measAppId;

    public LinkedList<ExpressionItem> expressionItemList;

    public LinkedList<DimensionFilterCreateVO> dimensionFilterList;

    public Integer applyType;

}
