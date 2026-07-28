package com.graphinsight.indicator.auto.service;

import com.baomidou.dynamic.datasource.annotation.DS;
import com.baomidou.mybatisplus.extension.service.IService;
import com.graphinsight.indicator.auto.entity.DwTable;

/**
 * <p>
 * 数仓物理表 服务类
 * </p>
 *
 * @since 2021-11-16
 */
@DS("mysql")
public interface IDwTableService extends IService<DwTable> {

}
