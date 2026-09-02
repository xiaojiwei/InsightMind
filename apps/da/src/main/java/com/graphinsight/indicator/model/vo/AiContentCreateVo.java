package com.graphinsight.indicator.model.vo;

import lombok.Data;

import javax.validation.constraints.NotEmpty;
import javax.validation.constraints.NotNull;


@Data
public class AiContentCreateVo {

    @NotNull
    private Integer sessionId;


    @NotNull
    private Object content;

    @NotEmpty
    private String roleType;

    @NotEmpty
    private String type;

    private String questType;

    private String category;

}
