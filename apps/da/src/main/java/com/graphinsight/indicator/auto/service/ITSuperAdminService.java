package com.graphinsight.indicator.auto.service;

import com.baomidou.dynamic.datasource.annotation.DS;
import com.baomidou.mybatisplus.extension.service.IService;
import com.graphinsight.indicator.auto.entity.TSuperAdmin;

/**
 * <p>
 * 超级管理员 服务类
 * </p>
 *
 * @author lixiaolong5
 * @since 2022-06-13
 */
@DS("mysql")
public interface ITSuperAdminService extends IService<TSuperAdmin> {

}
