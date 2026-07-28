package com.graphinsight.indicator.auto.mapper;

import com.baomidou.dynamic.datasource.annotation.DS;
import com.graphinsight.indicator.auto.entity.MeasureMonitor;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;

/**
 * <p>
 * 指标预警表 Mapper 接口
 * </p>
 *
 * @since 2022-10-11
 */
@DS("mysql")
public interface MeasureMonitorMapper extends BaseMapper<MeasureMonitor> {

}
