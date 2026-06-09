package com.graphinsight.indicator.auto.mapper;

import com.baomidou.dynamic.datasource.annotation.DS;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.graphinsight.indicator.auto.entity.DimAllValuesInfo;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import java.util.List;
import java.util.Set;

import org.apache.ibatis.annotations.Param;

/**
 * <p>
 * Mapper 接口
 * </p>
 *
 * @author lixiaolong
 * @since 2021-12-13
 */
@DS("mysql")
@Mapper
public interface DimAllValuesMapper extends BaseMapper<DimAllValuesInfo> {

    @Select("select username from user")
    Set<String> getAllUsername();

    //    @Update({"update `user` set `name` = #{name}, description = #{description}, department = #{department}, update_time = #{updateTime}",
//            ",`avatar` = #{avatar},`mobile` = #{mobile},`email` = #{email} where `id` = #{id}"})
//    int updateUserInfoById(User user);
//
    // @Select({"select * from `dim_all_values` where `value_text` like CONCAT('%', #{valueText}, '%') and `dim_code` in #{dimCode}"})
    List<DimAllValuesInfo> selectDimList(@Param("valueText") String valueText, @Param("dimCode") List dimCode);

    List<DimAllValuesInfo> selectListByLike(@Param("valueList") List<String> valueList, @Param("dimList") Set<String> dimList, @Param("dictList") List<String> dictList);

    List<DimAllValuesInfo> selectListByLikeName(@Param("valueList") List<String> valueList);

    List<DimAllValuesInfo> selectListByName(@Param("valueList") List<String> valueList);

    List<DimAllValuesInfo> selectAllDimList();

    void insertBatch(@Param("valueList") List<DimAllValuesInfo> dimAllValuesInfoList);

    DimAllValuesInfo selectInfoByDimAndValue(@Param("dimCode") String dimCode, @Param("valueText") String valueText);
    List<DimAllValuesInfo> selectInfoByDimCode(@Param("dimCode") String dimCode);

    // 获取正式数据
    List<DimAllValuesInfo> selectRealListByName(@Param("valueList") List<String> valueList, @Param("dimId") Set<Integer> dimCodeValueSet);

    List<DimAllValuesInfo> selectRealByLikeName(@Param("valueList") List<String> valueList, @Param("dimId") Set<Integer> dimCodeValueSet);
}
