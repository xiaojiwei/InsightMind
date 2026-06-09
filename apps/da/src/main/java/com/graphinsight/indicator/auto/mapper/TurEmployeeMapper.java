package com.graphinsight.indicator.auto.mapper;

import com.baomidou.dynamic.datasource.annotation.DS;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.graphinsight.indicator.auto.entity.User;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import java.util.Set;

/**
 * <p>
 *  Mapper 接口
 * </p>
 *
 */
@DS("mysql")
@Mapper
public interface TurEmployeeMapper extends BaseMapper<User> {

    @Select("select username from user")
    Set<String> getAllUsername();

//    @Update({"update `user` set `name` = #{name}, description = #{description}, department = #{department}, update_time = #{updateTime}",
//            ",`avatar` = #{avatar},`mobile` = #{mobile},`email` = #{email} where `id` = #{id}"})
//    int updateUserInfoById(User user);
//
    @Select({"select * from `user` where `username` = #{username} or `email` = #{username}"})
    User selectByUsername(String username);
}
