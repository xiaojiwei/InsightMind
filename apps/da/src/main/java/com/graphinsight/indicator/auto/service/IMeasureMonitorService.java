package com.graphinsight.indicator.auto.service;

import com.baomidou.dynamic.datasource.annotation.DS;
import com.baomidou.mybatisplus.extension.service.IService;
import com.graphinsight.indicator.auto.entity.MeasureMonitor;

/**
 * <p>
 * 指标预警表 服务类
 * </p>
 *
 * @since 2022-10-11
 */
@DS("mysql")
public interface IMeasureMonitorService extends IService<MeasureMonitor> {

}
