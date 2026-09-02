package com.graphinsight.indicator.model.vo;

import lombok.Data;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;


@Data
public class AiSessionUpdateVo {

    @NotNull
    private Integer sessionId;

    @NotBlank
    private String name;

}
