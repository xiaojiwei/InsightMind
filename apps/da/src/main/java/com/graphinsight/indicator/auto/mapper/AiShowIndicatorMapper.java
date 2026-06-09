package com.graphinsight.indicator.auto.mapper;

import com.baomidou.dynamic.datasource.annotation.DS;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.graphinsight.indicator.auto.entity.AiSearchInfo;
import com.graphinsight.indicator.auto.entity.AiShowIndicator;
import org.apache.ibatis.annotations.Select;

import java.util.List;
@DS("mysql")
public interface AiShowIndicatorMapper  extends BaseMapper<AiShowIndicator> {
    @Select("SELECT m_code FROM ai_show_indicator where is_del = 0")
    List<String> getAllShow();
}