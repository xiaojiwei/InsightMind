package com.graphinsight.indicator.auto.mapper;

import com.baomidou.dynamic.datasource.annotation.DS;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.graphinsight.indicator.auto.entity.AiSearchContext;
import com.graphinsight.indicator.auto.entity.AiSearchDefaultInfo;
import com.graphinsight.indicator.auto.entity.AiSearchInfo;
import com.graphinsight.indicator.model.vo.AiCollectInfoVo;

@DS("mysql")
public interface AiSearchDefaultInfoMapper extends BaseMapper<AiSearchDefaultInfo> {
    IPage<AiCollectInfoVo> getListByType(Page<AiCollectInfoVo> pageInfo,Integer analysisType);
    IPage<AiSearchInfo> getListInfoByType(Page<AiSearchInfo> pageInfo,Integer analysisType);
}