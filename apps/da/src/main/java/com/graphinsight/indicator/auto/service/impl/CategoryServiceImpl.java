package com.graphinsight.indicator.auto.service.impl;

import com.baomidou.dynamic.datasource.annotation.DS;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.graphinsight.indicator.auto.entity.Category;
import com.graphinsight.indicator.auto.mapper.CategoryMapper;
import com.graphinsight.indicator.auto.service.ICategoryService;
import org.springframework.stereotype.Service;

/**
 * <p>
 * 分类表 服务实现类
 * </p>
 *
 * @author lixiaolong
 * @since 2021-11-16
 */
@Service
@DS("mysql")

public class CategoryServiceImpl extends ServiceImpl<CategoryMapper, Category> implements ICategoryService {

}
