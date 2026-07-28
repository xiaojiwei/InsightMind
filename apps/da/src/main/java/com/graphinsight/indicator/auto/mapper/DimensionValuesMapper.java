package com.graphinsight.indicator.auto.mapper;

import com.baomidou.dynamic.datasource.annotation.DS;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.graphinsight.indicator.auto.entity.DimensionValues;

import java.util.List;

/**
 * <p>
 *  Mapper 接口
 * </p>
 *
 * @since 2022-02-15
 */
@DS("mysql")
public interface DimensionValuesMapper extends BaseMapper<DimensionValues> {
    List<DimensionValues> selectAllInfo();
}
