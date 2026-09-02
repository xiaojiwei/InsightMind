package com.graphinsight.indicator.model.vo;

import lombok.Data;

import javax.validation.constraints.NotNull;

/**
 * Date: 2022/8/22
 * Desc:
 */
@Data
public class NaturalDimConfigQueryVO {

    @NotNull(message = "指标ID不能为空")
    private Long measId;


    @NotNull(message = "模型ID不能为空")
    private Long modelId;


}
