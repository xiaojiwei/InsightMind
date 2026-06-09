package com.graphinsight.indicator.auto.service;

import com.baomidou.dynamic.datasource.annotation.DS;
import com.baomidou.mybatisplus.extension.service.IService;
import com.graphinsight.indicator.auto.entity.WidgetDetail;

/**
 * <p>
 * widget详情 服务类
 * </p>
 *
 * @author lixiaolong5
 * @since 2022-08-31
 */
@DS("mysql")
public interface IWidgetDetailService extends IService<WidgetDetail> {

}
