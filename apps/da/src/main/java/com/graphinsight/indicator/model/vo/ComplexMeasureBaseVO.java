package com.graphinsight.indicator.model.vo;

import com.graphinsight.indicator.annotation.DimenisonFilterList;
import com.graphinsight.indicator.annotation.ExpressionItemList;
import com.graphinsight.indicator.annotation.LeafCategoryId;
import com.graphinsight.indicator.enums.SqlAggFunType;
import io.swagger.annotations.ApiModelProperty;
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
    @ApiModelProperty(value = "模型ID")
    public Integer modelId;

    /**
     * 模型中文名
     */
    @ApiModelProperty(value = "模型中文名")
    public String modelCnName;

    /**
     * 模型英文名
     */
    @ApiModelProperty(value = "模型英文名")
    public String modelEnName;

    /**
     * 模型对应事实表名
     */
    @ApiModelProperty(value = "模型事实表名")
    public String modelTableName;

    private String description;


    /**
     * 主键
     */
    @ApiModelProperty(value = "主键，在更新指标时传入，更新时除了主键做了必填校验外，其余字段都不做必填校验。但是字段不传服务端无法区分是删除了这个字段还是对原字段不做改变，因此原有的字段无论是否修改过，都需要传给服务端",required = true)
    public Integer id;

    /**
     * 指标中文名，全局唯一
     */
    @NotBlank(message = "指标中文名不能为空")
    @ApiModelProperty(value = "指标中文名",required = true,example = "订单总量",notes = "指标字段中文名")
    public String cnName;

    /**
     * 指标英文名，全局唯一
     */
    @NotBlank(message = "指标英文名不能为空")
    @ApiModelProperty(value = "英文名",required = true)
    public String enName;


    @ExpressionItemList
    @ApiModelProperty(value = "表达式列表")
    public LinkedList<ExpressionItem> expressionItemList;

    @DimenisonFilterList
    @ApiModelProperty(value = "维度筛选列表,创建派生指标的时候传此字段")
    public LinkedList<DimensionFilterCreateVO> dimensionFilterList;

    /**
     *   ORIGIN(0, "原生指标"),
     *     DERIVED(1, "衍生指标|复合指标"),
     *     EXTENDED(2, "派生指标")
     */
    @ApiModelProperty(value = "指标类型",example = "0-原子指标 1-复合指标 2-派生指标")
    public Integer measureType;

    @ApiModelProperty(value = "聚合函数")
    private SqlAggFunType sqlAggFunType;

    /**
     * 叶子分类节点ID
     */
    @NotNull(message = "分类为空")
    @LeafCategoryId
    @ApiModelProperty(value = "叶子分类节点ID")
    public Integer leafCategoryId;

    @ApiModelProperty(value = "指标应用表ID,修改指标表达式时传入")
    private Integer measAppId;

    @ApiModelProperty(value = "原子指标的where条件")
    private String whereCondition;

    @ApiModelProperty(value = "原子指标的聚合列")
    private String columnEnName;

    @ApiModelProperty(value = "是否可用 0-不可用 1-可用")
    private Integer available;

    @ApiModelProperty(value = "维度自然日配置信息")
    private List<NaturalDimConfigVO> naturalDimConfig;
}
