package com.graphinsight.indicator.auto.mapper;

import com.baomidou.dynamic.datasource.annotation.DS;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.graphinsight.indicator.auto.entity.AiSearchInfo;
import com.graphinsight.indicator.auto.entity.WordInfos;
import com.graphinsight.indicator.model.vo.AiCollectInfoVo;

import java.util.List;
@DS("mysql")
public interface AiSearchInfoMapper  extends BaseMapper<AiSearchInfo> {
    IPage<AiSearchInfo> getListByUser(Page<AiSearchInfo> pageInfo, String user);
    IPage<AiSearchInfo> getListByUserId(Page<AiSearchInfo> pageInfo, String userId);
    IPage<AiSearchInfo> getHotListByUserId(Page<AiSearchInfo> pageInfo, String userId, Integer viewType);

    IPage<AiSearchInfo> gerHotInterpret(Page<AiSearchInfo> pageInfo, String userId, Integer viewType);

    List<AiSearchInfo> gerRecommend(String userId);
}