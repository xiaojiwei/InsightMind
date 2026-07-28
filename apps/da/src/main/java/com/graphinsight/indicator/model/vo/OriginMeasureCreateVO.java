package com.graphinsight.indicator.model.vo;

import com.graphinsight.indicator.annotation.LeafCategoryId;
import com.graphinsight.indicator.enums.SqlAggFunType;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

import javax.validation.constraints.NotNull;
import java.util.List;

/**
 * Date: 2022/2/24
 * Desc:
 */
@Data
public class OriginMeasureCreateVO extends BaseVO{

    @ApiModelProperty(value = "指标ID")
    private Integer id;

    @NotNull(message = "指标英文名不能为空")
    @ApiModelProperty(value = "指标英文名",required = true)
    private String enName;

    @NotNull(message = "指标中文名不能为空")
    @ApiModelProperty(value = "指标中文名",required = true)
    private String cnName;

    @NotNull(message = "字段名不能为空")
    @ApiModelProperty(value = "字段名",required = true)
    private String columnEnName;


    @NotNull(message = "模型ID不能为空")
    @ApiModelProperty(value = "模型ID",required = true)
    private Integer modelId;

    @ApiModelProperty(value = "指标应用ID")
    private Integer measAppId;

    @ApiModelProperty(value = "where 条件")
    private String whereCondition;

    @NotNull(message = "聚合函数不能为空")
    @ApiModelProperty(value = "聚合函数",required = true)
    private SqlAggFunType sqlAggFunType;

    private String description;

    @LeafCategoryId
    @ApiModelProperty(value = "叶子分类节点ID")
    Integer leafCategoryId;

    @ApiModelProperty(value = "维度归总配置")
    private List<NaturalDimConfigVO> naturalDimConfig;
}
