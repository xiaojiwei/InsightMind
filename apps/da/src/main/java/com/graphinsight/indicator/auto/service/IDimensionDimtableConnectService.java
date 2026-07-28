package com.graphinsight.indicator.auto.service;

import com.baomidou.dynamic.datasource.annotation.DS;
import com.baomidou.mybatisplus.extension.service.IService;
import com.graphinsight.indicator.auto.entity.DimensionDimtableConnect;

/**
 * <p>
 * 维度和维表的关联表 服务类
 * </p>
 *
 * @since 2021-11-18
 */
@DS("mysql")
public interface IDimensionDimtableConnectService extends IService<DimensionDimtableConnect> {

}
