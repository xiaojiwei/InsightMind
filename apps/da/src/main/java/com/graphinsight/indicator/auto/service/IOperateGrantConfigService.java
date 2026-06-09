package com.graphinsight.indicator.auto.service;

import com.baomidou.dynamic.datasource.annotation.DS;
import com.baomidou.mybatisplus.extension.service.IService;
import com.graphinsight.indicator.auto.entity.OperateGrantConfig;

/**
 * <p>
 *  服务类
 * </p>
 *
 * @author lixiaolong5
 * @since 2022-05-23
 */
@DS("mysql")
public interface IOperateGrantConfigService extends IService<OperateGrantConfig> {

}
