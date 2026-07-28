package com.graphinsight.indicator.auto.mapper;

import com.baomidou.dynamic.datasource.annotation.DS;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.graphinsight.indicator.auto.entity.Measure;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;
import java.util.Set;

/**
 * <p>
 * 指标表 Mapper 接口
 * </p>
 *
 * @since 2021-11-16
 */
@DS("mysql")
public interface MeasureMapper extends BaseMapper<Measure> {

    @Select({"select * from measure where code = #{code}"})
    Measure selectByCode(@Param("code") String code);

    List<Measure> selectListByCode(@Param("codeSet") Set<com.graphinsight.indicator.model.Measure> codeSet);
}
