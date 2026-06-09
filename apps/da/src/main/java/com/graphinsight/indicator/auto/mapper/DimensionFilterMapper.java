package com.graphinsight.indicator.auto.mapper;

import com.baomidou.dynamic.datasource.annotation.DS;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.graphinsight.indicator.auto.entity.DimensionFilter;

/**
 * <p>
 *  Mapper 接口
 * </p>
 *
 * @author lixiaolong5
 * @since 2022-02-11
 */
@DS("mysql")
public interface DimensionFilterMapper extends BaseMapper<DimensionFilter> {

}
