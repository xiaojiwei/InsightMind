package com.graphinsight.indicator.auto.mapper;

import com.baomidou.dynamic.datasource.annotation.DS;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.graphinsight.indicator.auto.entity.DimensionDimtableConnect;

/**
 * <p>
 * 维度和维表的关联表 Mapper 接口
 * </p>
 *
 * @since 2021-11-18
 */
@DS("mysql")
public interface DimensionDimtableConnectMapper extends BaseMapper<DimensionDimtableConnect> {

}
