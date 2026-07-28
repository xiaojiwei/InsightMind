package com.graphinsight.indicator.auto.service;

import com.baomidou.dynamic.datasource.annotation.DS;
import com.baomidou.mybatisplus.extension.service.IService;
import com.graphinsight.indicator.auto.entity.Department;

/**
 * <p>
 *  服务类
 * </p>
 *
 * @since 2022-01-25
 */
@DS("mysql")
public interface IDepartmentService extends IService<Department> {

}
