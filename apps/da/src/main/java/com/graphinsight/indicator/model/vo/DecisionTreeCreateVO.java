package com.graphinsight.indicator.model.vo;

import lombok.Data;

import java.util.List;

/**
 * Date: 2022/6/16
 * Desc:
 */
@Data
public class DecisionTreeCreateVO extends BaseVO {

    private String measureCode;

    private List<ExpressionItem> expressionItemVOList;

}
