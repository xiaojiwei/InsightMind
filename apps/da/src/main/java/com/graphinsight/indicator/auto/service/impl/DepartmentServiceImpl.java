package com.graphinsight.indicator.auto.service.impl;

import com.baomidou.dynamic.datasource.annotation.DS;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.graphinsight.indicator.auto.entity.Department;
import com.graphinsight.indicator.auto.mapper.DepartmentMapper;
import com.graphinsight.indicator.auto.service.IDepartmentService;
import org.springframework.stereotype.Service;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author lixiaolong5
 * @since 2022-01-25
 */
@Service
@DS("mysql")
public class DepartmentServiceImpl extends ServiceImpl<DepartmentMapper, Department> implements IDepartmentService {

}
