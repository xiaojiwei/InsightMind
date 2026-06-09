package com.graphinsight.indicator.model.vo;

import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

import javax.validation.constraints.Max;
import javax.validation.constraints.Min;
import javax.validation.constraints.NotBlank;
import java.util.List;


@Data
public class AiSceneDetailVo {
    @ApiModelProperty(value = "场景id", required = true, example = "_statistics_")
    @NotBlank
    private String sceneId;
    @ApiModelProperty(value = "关键字类型 dim 维度 meas 指标", example = "_statistics_")
    private String keyType;
    @ApiModelProperty(value = "关键字搜索", example = "_statistics_")
    private String keyword;

}
