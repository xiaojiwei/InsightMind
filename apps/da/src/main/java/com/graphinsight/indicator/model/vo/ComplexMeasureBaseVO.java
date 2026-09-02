package com.graphinsight.indicator.model.vo;

import com.graphinsight.indicator.annotation.DimenisonFilterList;
import com.graphinsight.indicator.annotation.ExpressionItemList;
import com.graphinsight.indicator.annotation.LeafCategoryId;
import com.graphinsight.indicator.enums.SqlAggFunType;
import lombok.Data;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import java.util.LinkedList;
import java.util.List;

/**
 * Date: 2022/2/14
 * Desc:
 */
@Data
public class ComplexMeasureBaseVO extends BaseVO{

    /**
     * 模型ID
     */
    public Integer modelId;

    /**
     * 模型中文名
     */
    public String modelCnName;

    /**
     * 模型英文名
     */
    public String modelEnName;

    /**
     * 模型对应事实表名
     */
    public String modelTableName;

    private String description;


    /**
     * 主键
     */
    public Integer id;

    /**
     * 指标中文名，全局唯一
     */
    @NotBlank(message = "指标中文名不能为空")
    public String cnName;

    /**
     * 指标英文名，全局唯一
     */
    @NotBlank(message = "指标英文名不能为空")
    public String enName;


    @ExpressionItemList
    public LinkedList<ExpressionItem> expressionItemList;

    @DimenisonFilterList
    public LinkedList<DimensionFilterCreateVO> dimensionFilterList;

    /**
     *   ORIGIN(0, "原生指标"),
     *     DERIVED(1, "衍生指标|复合指标"),
     *     EXTENDED(2, "派生指标")
     */
    public Integer measureType;

    private SqlAggFunType sqlAggFunType;

    /**
     * 叶子分类节点ID
     */
    @NotNull(message = "分类为空")
    @LeafCategoryId
    public Integer leafCategoryId;

    private Integer measAppId;

    private String whereCondition;

    private String columnEnName;

    private Integer available;

    private List<NaturalDimConfigVO> naturalDimConfig;
}
