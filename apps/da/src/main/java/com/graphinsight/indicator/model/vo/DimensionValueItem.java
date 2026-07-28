package com.graphinsight.indicator.model.vo;

import lombok.Data;

import javax.validation.constraints.NotBlank;

/**
 * Date: 2022/2/15
 * Desc:
 */
@Data
public class DimensionValueItem {
    @NotBlank(message = "查询字段不能为空")
    private String queryField;

    @NotBlank(message = "显示字段不能为空")
    private String displayField;
}
