package com.graphinsight.indicator.auto.mapper;

import com.baomidou.dynamic.datasource.annotation.DS;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.graphinsight.indicator.auto.entity.WordValues;
import com.graphinsight.indicator.model.Page;
import com.graphinsight.indicator.model.vo.AiBusinessListVo;
import com.graphinsight.indicator.model.vo.AiBusinessSearchVo;
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
public interface WordValuesMapper extends BaseMapper<WordValues> {

    List<WordValues> selectKeyList(@Param("valueList") List<String> valueList);
    List<WordValues> selectValueList(@Param("valueList") List<String> valueList);
    List<WordValues> selectInfoList();

    void insertAliase(@Param("valueList") List<String> valueList,@Param("valueKey") String valueKey);
    void delAliase(@Param("valueKey") String valueKey);
    //List<DimAllValues> selectListByLike(@Param("valueList") List<String> valueList,@Param("dimList") Set<String> dimList,@Param("dictList") List<String> dictList);
    void insertBatch(@Param("valueList") List<WordValues> valuesInfoList);

    IPage<AiBusinessListVo> selectPageInfo(@Param("page") IPage page, @Param("searchVo") AiBusinessSearchVo searchVo);
    IPage<AiBusinessListVo> selectKeyWordPageInfo(@Param("page") IPage page, @Param("searchVo") AiBusinessSearchVo searchVo);
}
