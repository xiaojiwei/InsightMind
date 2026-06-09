package com.graphinsight.indicator.auto.mapper;

import com.baomidou.dynamic.datasource.annotation.DS;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.graphinsight.indicator.auto.entity.AiSearchInfo;
import com.graphinsight.indicator.auto.entity.AiUserCollect;
import com.graphinsight.indicator.model.vo.AiCollectInfoVo;

import java.util.Map;

@DS("mysql")
public interface AiUserCollectMapper  extends BaseMapper<AiUserCollect>  {

    IPage<AiCollectInfoVo> getListByUserId(Page<AiCollectInfoVo> pageInfo, String userId);

}