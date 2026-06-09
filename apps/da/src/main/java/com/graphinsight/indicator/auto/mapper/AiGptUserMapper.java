package com.graphinsight.indicator.auto.mapper;

import com.baomidou.dynamic.datasource.annotation.DS;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.graphinsight.indicator.entity.AiGptUser;

/**
* @author houfenglei
*/
@DS("mysql")
public interface AiGptUserMapper extends BaseMapper<AiGptUser> {

}