package com.graphinsight.indicator.auto.mapper;

import com.baomidou.dynamic.datasource.annotation.DS;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.graphinsight.indicator.auto.entity.DimensionApplication;

/**
 * <p>
 * 维度应用表 Mapper 接口
 * </p>
 *
 * @author lixiaolong
 * @since 2021-11-16
 */
@DS("mysql")
public interface DimensionApplicationMapper extends BaseMapper<DimensionApplication> {

}
