package com.graphinsight.indicator.doris.mapper;

import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.graphinsight.indicator.doris.entity.DimWithoutTable;

/**
 * <p>
 * SCHEMA Mapper 接口
 * </p>
 *
 * @since 2021-11-17
 */
@TableName(schema = "eps_test")
public interface DimWithoutTableMapper extends BaseMapper<DimWithoutTable> {

}
