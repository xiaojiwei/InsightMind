package com.graphinsight.indicator.auto.service;

import com.baomidou.dynamic.datasource.annotation.DS;
import com.baomidou.mybatisplus.extension.service.IService;
import com.graphinsight.indicator.auto.entity.MeasureApplication;

/**
 * <p>
 * 指标应用表 服务类
 * </p>
 *
 * @author lixiaolong
 * @since 2021-11-16
 */
@DS("mysql")
public interface IMeasureApplicationService extends IService<MeasureApplication> {

}
