package com.graphinsight.indicator.auto.service.impl;

import com.baomidou.dynamic.datasource.annotation.DS;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.graphinsight.indicator.auto.entity.Hierarchy;
import com.graphinsight.indicator.auto.mapper.HierarchyMapper;
import com.graphinsight.indicator.auto.service.IHierarchyService;
import org.springframework.stereotype.Service;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author lixiaolong5
 * @since 2022-01-28
 */
@Service
@DS("mysql")
public class HierarchyServiceImpl extends ServiceImpl<HierarchyMapper, Hierarchy> implements IHierarchyService {

}
