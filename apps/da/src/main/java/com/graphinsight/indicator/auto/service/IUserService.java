package com.graphinsight.indicator.auto.service;

import com.baomidou.dynamic.datasource.annotation.DS;
import com.baomidou.mybatisplus.extension.service.IService;
import com.graphinsight.indicator.auto.entity.User;

/**
 * <p>
 *  服务类
 * </p>
 *
 * @author lixiaolong
 * @since 2021-12-13
 */
@DS("mysql")
public interface IUserService extends IService<User> {

}
