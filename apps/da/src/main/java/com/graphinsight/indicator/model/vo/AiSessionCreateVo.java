package com.graphinsight.indicator.model.vo;

import io.swagger.annotations.ApiModelProperty;
import lombok.Data;
import lombok.NonNull;

import javax.validation.constraints.NotBlank;


@Data
public class AiSessionCreateVo {

    @NotBlank
    @ApiModelProperty(value = "会话名称", required = true, example = "_statistics_")
    private String name;

    @ApiModelProperty(value = "场景id", example = "_statistics_")
    private String sceneId;

    @ApiModelProperty(value = "场景中的内容", example = "_statistics_")
    private Object sceneContent;

    @ApiModelProperty(value = "会话类型", example = "_statistics_")
    public String type = "dataGpt";

}
