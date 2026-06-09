package com.graphinsight.indicator.auto.mapper;

import com.baomidou.dynamic.datasource.annotation.DS;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.graphinsight.indicator.auto.entity.AiSearchContext;
import com.graphinsight.indicator.auto.entity.AiSearchInfo;
@DS("mysql")
public interface AiSearchContextMapper extends BaseMapper<AiSearchContext>  {
}