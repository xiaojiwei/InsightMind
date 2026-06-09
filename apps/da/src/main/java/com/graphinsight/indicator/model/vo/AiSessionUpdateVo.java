package com.graphinsight.indicator.model.vo;

import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;


@Data
public class AiSessionUpdateVo {

    @NotNull
    @ApiModelProperty(value = "历史记录id", required = true, example = "1")
    private Integer sessionId;

    @ApiModelProperty(value = "会话名称",required = true,example = "_statistics_")
    @NotBlank
    private String name;

}
