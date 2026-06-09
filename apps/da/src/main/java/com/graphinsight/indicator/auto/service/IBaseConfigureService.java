package com.graphinsight.indicator.auto.service;

import com.baomidou.dynamic.datasource.annotation.DS;
import com.baomidou.mybatisplus.extension.service.IService;
import com.graphinsight.indicator.auto.entity.BaseConfigure;

/**
 * <p>
 *  服务类
 * </p>
 *
 * @author lixiaolong5
 * @since 2022-03-07
 */
@DS("mysql")
public interface IBaseConfigureService extends IService<BaseConfigure> {

}
