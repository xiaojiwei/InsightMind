package com.graphinsight.indicator.auto.service;

import com.baomidou.dynamic.datasource.annotation.DS;
import com.baomidou.mybatisplus.extension.service.IService;
import com.graphinsight.indicator.auto.entity.DimensionFilter;

/**
 * <p>
 *  服务类
 * </p>
 *
 * @since 2022-02-11
 */
@DS("mysql")
public interface IDimensionFilterService extends IService<DimensionFilter> {

}
