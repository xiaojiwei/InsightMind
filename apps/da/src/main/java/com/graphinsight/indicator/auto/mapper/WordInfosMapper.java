package com.graphinsight.indicator.auto.mapper;

import com.baomidou.dynamic.datasource.annotation.DS;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.graphinsight.indicator.auto.entity.WordInfos;
import com.graphinsight.indicator.auto.entity.WordValues;
import com.graphinsight.indicator.model.vo.MeasureUpdateVO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * <p>
 *  Mapper 接口
 * </p>
 *
 * @since 2021-12-13
 */
@DS("mysql")
@Mapper
public interface WordInfosMapper extends BaseMapper<WordInfos> {

    List<WordInfos> selectKeyList(@Param("valueList") List<String> valueList);
    List<WordInfos> selectInfoList();

}
