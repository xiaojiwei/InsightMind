package com.graphinsight.indicator.auto.service;

import com.baomidou.dynamic.datasource.annotation.DS;
import com.baomidou.mybatisplus.extension.service.IService;
import com.graphinsight.indicator.auto.entity.Level;

/**
 * <p>
 *  服务类
 * </p>
 *
 * @author lixiaolong5
 * @since 2022-01-28
 */
@DS("mysql")
public interface ILevelService extends IService<Level> {

}
