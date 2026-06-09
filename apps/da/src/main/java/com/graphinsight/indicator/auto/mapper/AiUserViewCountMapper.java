package com.graphinsight.indicator.auto.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.graphinsight.indicator.auto.entity.AiSearchInfo;
import com.graphinsight.indicator.auto.entity.AiUserViewCount;

public interface AiUserViewCountMapper   extends BaseMapper<AiUserViewCount> {
    /**
     * @mbg.generated generated automatically, do not modify!
     */
    AiUserViewCount selectByPrimaryKey(Long id);
}