package com.graphinsight.indicator.auto.service;

import com.baomidou.dynamic.datasource.annotation.DS;
import com.graphinsight.indicator.auto.entity.DataSource;
import com.baomidou.mybatisplus.extension.service.IService;

/**
 * <p>
 * 数据源表 服务类
 * </p>
 *
 * @author lixiaolong5
 * @since 2022-09-09
 */
@DS("mysql")
public interface IDataSourceService extends IService<DataSource> {

}
