package com.graphinsight.indicator.auto.service;

import com.baomidou.dynamic.datasource.annotation.DS;
import com.baomidou.mybatisplus.extension.service.IService;
import com.graphinsight.indicator.auto.entity.Widget;

/**
 * <p>
 * widget表 服务类
 * </p>
 *
 * @author lixiaolong5
 * @since 2022-08-31
 */
@DS("mysql")
public interface IWidgetService extends IService<Widget> {

}
