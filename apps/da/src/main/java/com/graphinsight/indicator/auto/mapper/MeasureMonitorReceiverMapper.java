package com.graphinsight.indicator.auto.mapper;

import com.baomidou.dynamic.datasource.annotation.DS;
import com.graphinsight.indicator.auto.entity.MeasureMonitorReceiver;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;

/**
 * <p>
 * 告警接收人 Mapper 接口
 * </p>
 *
 * @author lixiaolong5
 * @since 2022-10-17
 */
@DS("mysql")
public interface MeasureMonitorReceiverMapper extends BaseMapper<MeasureMonitorReceiver> {

}
