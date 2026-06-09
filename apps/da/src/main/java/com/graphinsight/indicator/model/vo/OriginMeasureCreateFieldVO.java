package com.graphinsight.indicator.model.vo;

import com.graphinsight.indicator.annotation.LeafCategoryId;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

import javax.validation.constraints.NotNull;

/**
 * Author: lixiaolong
 * Date: 2022/2/24
 * Desc:
 */
@Data
public class OriginMeasureCreateFieldVO extends BaseVO {

    @ApiModelProperty(value = "字段英文名",required = true)
    private String enName;

    @ApiModelProperty(value = "字段中文名",required = true)
    private String cnName;

    @ApiModelProperty(value = "要格式化的日期类型")
    private Integer viewType;

    @NotNull(message = "字段类型不能为空")
    @ApiModelProperty(value = "字段类型1-指标 2-维度")
    private Integer type;

    @NotNull(message = "分类ID不能为空")
    @LeafCategoryId
    @ApiModelProperty(value = "分类节点ID",required = true)
    private Integer leafCategoryId;

    private String description;
}
