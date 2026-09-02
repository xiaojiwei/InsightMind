package com.graphinsight.indicator.model.vo;

import com.graphinsight.indicator.annotation.DimenisonFilterList;
import com.graphinsight.indicator.annotation.ExpressionItemList;
import com.graphinsight.indicator.enums.SqlAggFunType;
import lombok.Data;

import javax.validation.constraints.NotNull;
import java.util.LinkedList;
import java.util.List;

/**
 * Date: 2022/4/14
 * Desc:
 */
@Data
public class MeasureExpBaseVO extends BaseVO {

    @NotNull(message = "指标ID不能为空")
    public Integer measureId;

    public Integer modelId;

    @NotNull(message = "指标类型不能为空")
    public Integer measureType;

    @ExpressionItemList
    public LinkedList<ExpressionItem> expressionItemList;

    @DimenisonFilterList
    public LinkedList<DimensionFilterCreateVO> dimensionFilterList;

    public String whereCondition;

    public SqlAggFunType sqlAggFunType;


    public String columnEnName;

    private List<NaturalDimConfigVO> naturalDimConfig;

}
