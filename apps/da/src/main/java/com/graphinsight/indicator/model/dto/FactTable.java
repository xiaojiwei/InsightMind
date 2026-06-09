package com.graphinsight.indicator.model.dto;

import com.graphinsight.indicator.enums.MeasureType;
import com.graphinsight.indicator.model.vo.ExpressionItem;
import lombok.Data;

import java.util.LinkedList;

/**
 * Author: lixiaolong
 * Date: 2022/8/9
 * Desc:
 */
@Data
public class FactTable {

    private String tableName;

    private String schemaName;

    private String columnName;

    /**
     * 指标类型
     */
    private MeasureType measureType;

    private LinkedList<ExpressionItem> expressionItems;

}
