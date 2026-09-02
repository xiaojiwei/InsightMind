package com.graphinsight.indicator.model.vo;

import lombok.Data;
import lombok.NonNull;

import javax.validation.constraints.NotBlank;


@Data
public class AiSessionCreateVo {

    @NotBlank
    private String name;

    private String sceneId;

    private Object sceneContent;

    public String type = "dataGpt";

}
