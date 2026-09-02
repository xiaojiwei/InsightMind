package com.graphinsight.indicator.model.vo;

import com.graphinsight.indicator.annotation.LeafCategoryId;
import lombok.Data;

import javax.validation.constraints.NotNull;

/**
 * Date: 2022/2/24
 * Desc:
 */
@Data
public class OriginMeasureCreateFieldVO extends BaseVO {

    private String enName;

    private String cnName;

    private Integer viewType;

    @NotNull(message = "字段类型不能为空")
    private Integer type;

    @NotNull(message = "分类ID不能为空")
    @LeafCategoryId
    private Integer leafCategoryId;

    private String description;
}
