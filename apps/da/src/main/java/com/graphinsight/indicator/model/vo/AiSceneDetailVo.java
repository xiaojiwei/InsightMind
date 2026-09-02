package com.graphinsight.indicator.model.vo;

import lombok.Data;

import javax.validation.constraints.Max;
import javax.validation.constraints.Min;
import javax.validation.constraints.NotBlank;
import java.util.List;


@Data
public class AiSceneDetailVo {
    @NotBlank
    private String sceneId;
    private String keyType;
    private String keyword;

}
