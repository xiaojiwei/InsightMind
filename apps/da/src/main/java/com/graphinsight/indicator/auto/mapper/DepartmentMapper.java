package com.graphinsight.indicator.auto.mapper;

import com.baomidou.dynamic.datasource.annotation.DS;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.graphinsight.indicator.auto.entity.Department;
import org.apache.ibatis.annotations.Select;

import java.util.Set;

/**
 * <p>
 *  Mapper 接口
 * </p>
 *
 * @author lixiaolong5
 * @since 2022-01-25
 */
@DS("mysql")
public interface DepartmentMapper extends BaseMapper<Department> {

    @Select("select id_path from department")
    Set<String> getAllIdPath();

}
