package com.graphinsight.indicator.auto.mapper;

import com.baomidou.dynamic.datasource.annotation.DS;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.graphinsight.indicator.auto.entity.AiQuestionInfo;
import com.graphinsight.indicator.model.vo.*;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
* @author houfenglei
*/
@DS("mysql")
public interface AiQuestionInfoMapper extends BaseMapper<AiQuestionInfo> {
    IPage<AiQuestionInfoVO> selectPageInfo(@Param("page") IPage page, @Param("searchVo") AiQuestionInfoPageParam searchVo);
    List<AiQuestionCountVO> selectCountInfo(@Param("searchVo") AiQuestionInfoPageParam searchVo);

}