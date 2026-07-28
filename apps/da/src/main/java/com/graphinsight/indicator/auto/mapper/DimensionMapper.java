package com.graphinsight.indicator.auto.mapper;

import com.baomidou.dynamic.datasource.annotation.DS;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.graphinsight.indicator.auto.entity.Dimension;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;
import java.util.Set;

/**
 * <p>
 * 维度表 Mapper 接口
 * </p>
 *
 * @since 2021-11-16
 */
@DS("mysql")
public interface DimensionMapper extends BaseMapper<Dimension> {
    @Select({"select * from dimension where code = #{code}"})
    Dimension selectByCode(@Param("code") String code);

    List<Dimension> selectListByName(@Param("nameList") List<String> nameList,@Param("dimList") Set<String> dimList);
    List<Dimension> selectListInfoByLikeName(@Param("nameList") List<String> nameList);
    List<Dimension> selectListInfoByName(@Param("nameList") List<String> nameList);

    // 获取正式数据
    List<Dimension> selectRealInfoByName(@Param("nameList") List<String> nameList, @Param("dimensionId") Set<Integer> dimensionSet);
    List<Dimension> selectRealInfoByLikeName(@Param("nameList") List<String> nameList,@Param("dimensionId") Set<Integer> dimensionSet);
}
