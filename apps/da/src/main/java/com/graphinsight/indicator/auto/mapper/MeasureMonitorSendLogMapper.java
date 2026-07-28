package com.graphinsight.indicator.auto.mapper;

import com.baomidou.dynamic.datasource.annotation.DS;
import com.graphinsight.indicator.auto.entity.MeasureMonitorSendLog;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;

/**
 * <p>
 * 预警发送日志表 Mapper 接口
 * </p>
 *
 * @since 2022-10-17
 */
@DS("mysql")
public interface MeasureMonitorSendLogMapper extends BaseMapper<MeasureMonitorSendLog> {

}
