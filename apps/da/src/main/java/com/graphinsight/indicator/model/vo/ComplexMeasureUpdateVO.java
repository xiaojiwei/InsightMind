package com.graphinsight.indicator.model.vo;

import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

import javax.validation.constraints.NotNull;

/**
 * Date: 2022/2/9
 * Desc:
 */
@Data
public class ComplexMeasureUpdateVO extends ComplexMeasureBaseVO{

    @NotNull(message = "指标应用表Id不能为空")
    @ApiModelProperty(value = "指标应用表ID,修改指标表达式时传入")
    private Integer measAppId;

}
