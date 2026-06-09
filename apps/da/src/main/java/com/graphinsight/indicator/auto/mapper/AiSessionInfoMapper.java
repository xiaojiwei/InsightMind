package com.graphinsight.indicator.auto.mapper;

import com.baomidou.dynamic.datasource.annotation.DS;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.graphinsight.indicator.auto.entity.AiSearchInfo;
import com.graphinsight.indicator.auto.entity.AiSessionInfo;

import java.util.List;
@DS("mysql")
public interface AiSessionInfoMapper extends BaseMapper<AiSessionInfo> {
    IPage<AiSessionInfo> getListByUser(Page<AiSearchInfo> pageInfo, String user);
}