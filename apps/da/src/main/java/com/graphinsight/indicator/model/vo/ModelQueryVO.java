package com.graphinsight.indicator.model.vo;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

/**
 * @Description: 指标查询参数
 * @Date: 2021/11/16
 */
@Data
@ApiModel
public class ModelQueryVO extends BaseVO{
    /**
     * cnName
     */
    @ApiModelProperty(value = "关键字 中英文名",example = "_statistics_")
    private String keyword;

    @ApiModelProperty(value = "分类ID",example = "1")
    private Integer categoryId;

    private Integer measId;

    private Integer dimId;

}
