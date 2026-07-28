package com.graphinsight.indicator.model.vo;

import com.graphinsight.indicator.annotation.DimenisonFilterList;
import com.graphinsight.indicator.annotation.ExpressionItemList;
import com.graphinsight.indicator.enums.SqlAggFunType;
import io.swagger.annotations.ApiModelProperty;
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
    @ApiModelProperty(value = "指标类型 0-原子指标 1-复合指标 2-衍生指标")
    public Integer measureType;

    @ApiModelProperty(value = "表达式列表")
    @ExpressionItemList
    public LinkedList<ExpressionItem> expressionItemList;

    @DimenisonFilterList
    @ApiModelProperty(value = "维度筛选列表,创建派生指标的时候传此字段")
    public LinkedList<DimensionFilterCreateVO> dimensionFilterList;

    @ApiModelProperty(value = "where 条件,仅原子指标支持")
    public String whereCondition;

    @ApiModelProperty(value = "聚合函数,仅原子指标支持")
    public SqlAggFunType sqlAggFunType;


    @ApiModelProperty(value = "字段名，仅原子指标支持")
    public String columnEnName;

    @ApiModelProperty(value = "维度归总配置")
    private List<NaturalDimConfigVO> naturalDimConfig;

}
