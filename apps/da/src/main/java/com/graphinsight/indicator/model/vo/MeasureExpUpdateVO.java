package com.graphinsight.indicator.model.vo;

import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

import javax.validation.constraints.NotNull;
import java.util.List;

/**
 * Author: lixiaolong
 * Date: 2022/4/14
 * Desc:
 */
@Data
public class MeasureExpUpdateVO extends MeasureExpBaseVO {

    @NotNull(message = "指标应用ID不能为空")
    @ApiModelProperty(value = "指标应用ID")
    private Integer measAppId;

    @ApiModelProperty(value = "维度归总配置")
    private List<NaturalDimConfigVO> naturalDimConfig;

}
