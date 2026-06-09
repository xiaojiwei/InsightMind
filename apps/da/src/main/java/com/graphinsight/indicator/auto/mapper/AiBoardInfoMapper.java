package com.graphinsight.indicator.auto.mapper;

import com.baomidou.dynamic.datasource.annotation.DS;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.graphinsight.indicator.auto.entity.AiBoardInfo;
import com.graphinsight.indicator.model.vo.AiBoardInfoPageParam;
import com.graphinsight.indicator.model.vo.AiBoardInfoVO;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * @author houfenglei
 */
@DS("mysql")
public interface AiBoardInfoMapper extends BaseMapper<AiBoardInfo> {
    IPage<AiBoardInfoVO> selectPageInfo(@Param("page") IPage page, @Param("searchVo") AiBoardInfoPageParam searchVo);

    List<AiBoardInfo> selectLikeListByName(@Param("valueList") List<String> valueList);
    List<AiBoardInfo> selectListByName(@Param("valueList") List<String> valueList);
}