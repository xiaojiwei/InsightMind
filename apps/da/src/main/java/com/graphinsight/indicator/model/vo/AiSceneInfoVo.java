package com.graphinsight.indicator.model.vo;

import com.graphinsight.indicator.auto.entity.Dimension;
import com.graphinsight.indicator.enums.SceneType;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;


@Data
public class AiSceneInfoVo  {

    private String sceneId;
    private SceneType sceneType;
    private String sceneName;
    private String description;
    private List<CategoryNodeItem> measureBasicInfoVOS = new ArrayList<>();
    private List<Dimension> dimension = new ArrayList<>();

}
