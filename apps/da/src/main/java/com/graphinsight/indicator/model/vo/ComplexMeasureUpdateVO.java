package com.graphinsight.indicator.model.vo;

import lombok.Data;

import javax.validation.constraints.NotNull;

/**
 * Date: 2022/2/9
 * Desc:
 */
@Data
public class ComplexMeasureUpdateVO extends ComplexMeasureBaseVO{

    @NotNull(message = "指标应用表Id不能为空")
    private Integer measAppId;

}
