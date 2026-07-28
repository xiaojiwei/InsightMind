package com.graphinsight.indicator.auto.service;

import com.baomidou.dynamic.datasource.annotation.DS;
import com.baomidou.mybatisplus.extension.service.IService;
import com.graphinsight.indicator.auto.entity.Dimension;

/**
 * <p>
 * 维度表 服务类
 * </p>
 *
 * @since 2021-11-16
 */
@DS("mysql")
public interface IDimensionService extends IService<Dimension> {

}
