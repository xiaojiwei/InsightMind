package com.graphinsight.indicator.auto.service;

import com.baomidou.dynamic.datasource.annotation.DS;
import com.baomidou.mybatisplus.extension.service.IService;
import com.graphinsight.indicator.auto.entity.DimensionOperator;

/**
 * <p>
 *  服务类
 * </p>
 *
 * @since 2022-02-11
 */
@DS("mysql")
public interface IDimensionOperatorService extends IService<DimensionOperator> {

}
