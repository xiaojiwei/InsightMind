package com.graphinsight.indicator.model.vo;

import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

import javax.validation.constraints.NotEmpty;
import javax.validation.constraints.NotNull;


@Data
public class AiContentCreateVo {

    @ApiModelProperty(value = "会话id", required = true, example = "_statistics_")
    @NotNull
    private Integer sessionId;


    @ApiModelProperty(value = "会话内容", required = true, example = "_statistics_")
    @NotNull
    private Object content;

    @ApiModelProperty(value = "会话角色 user 用户 system 系统", required = true, example = "_statistics_")
    @NotEmpty
    private String roleType;

    @ApiModelProperty(value = "类型 dataGpt ", required = true, example = "_statistics_")
    @NotEmpty
    private String type;

    @ApiModelProperty(value = "问题类型 explain 解读 impute 归因 ",  example = "_statistics_")
    private String questType;

    @ApiModelProperty(value = "类别 0 新会话 1 追问 ",  example = "_statistics_")
    private String category;

}
