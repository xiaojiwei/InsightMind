package com.graphinsight.indicator.auto.mapper;

import com.baomidou.dynamic.datasource.annotation.DS;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.graphinsight.indicator.auto.entity.ContentValues;
import com.graphinsight.indicator.auto.entity.WordValues;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * <p>
 *  Mapper 接口
 * </p>
 *
 * @author lixiaolong
 * @since 2021-12-13
 */
@DS("mysql")
@Mapper
public interface ContentValuesMapper extends BaseMapper<ContentValues> {

}
