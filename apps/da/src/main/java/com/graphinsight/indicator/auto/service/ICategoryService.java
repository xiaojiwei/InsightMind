package com.graphinsight.indicator.auto.service;

import com.baomidou.dynamic.datasource.annotation.DS;
import com.baomidou.mybatisplus.extension.service.IService;
import com.graphinsight.indicator.auto.entity.Category;

/**
 * <p>
 * 分类表 服务类
 * </p>
 *
 * @author lixiaolong
 * @since 2021-11-16
 */
@DS("mysql")
public interface ICategoryService extends IService<Category> {

}
