package com.graphinsight.indicator.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.graphinsight.indicator.auto.entity.AiSearchInfo;
import com.graphinsight.indicator.auto.entity.Dimension;
import com.graphinsight.indicator.auto.entity.Measure;
import com.graphinsight.indicator.model.vo.*;

import java.util.List;


public interface AiSceneService {

    List<AiSceneInfoVo> sceneList();

    List<AiSceneInfoVo> marketSceneList(AiMarkerDetailVo aiSceneDetailVo);

    List<CategoryNodeItem> sceneMeasDetail(AiSceneDetailVo aiSceneDetailVo);
    List<Dimension> sceneDimDetail(AiSceneDetailVo aiSceneDetailVo);
    PageVO<CategoryNodeItem> marketMeasDetail(AiMarkerDetailVo aiSceneDetailVo);
    PageVO<Dimension> marketDimDetail(AiMarkerDetailVo aiSceneDetailVo);
}
