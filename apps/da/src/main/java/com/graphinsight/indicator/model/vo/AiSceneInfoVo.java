package com.graphinsight.indicator.model.vo;

import com.graphinsight.indicator.auto.entity.Dimension;
import com.graphinsight.indicator.enums.SceneType;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;


@Data
public class AiSceneInfoVo  {

    @ApiModelProperty(value = "场景id",example = "_statistics_")
    private String sceneId;
    @ApiModelProperty(value = "场景类型 指标 measure 主数据 mdm",example = "_statistics_")
    private SceneType sceneType;
    @ApiModelProperty(value = "场景名称 组织kpi",example = "_statistics_")
    private String sceneName;
    @ApiModelProperty(value = "场景描述",example = "_statistics_")
    private String description;
    @ApiModelProperty(value = "场景中指标列表",example = "_statistics_")
    private List<CategoryNodeItem> measureBasicInfoVOS = new ArrayList<>();
    @ApiModelProperty(value = "场景中维度列表",example = "_statistics_")
    private List<Dimension> dimension = new ArrayList<>();

}
