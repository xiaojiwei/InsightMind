package com.graphinsight.indicator.auto.service.impl;

import com.baomidou.dynamic.datasource.annotation.DS;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.graphinsight.indicator.auto.entity.ComplexMeasureDependencyTree;
import com.graphinsight.indicator.auto.mapper.ComplexMeasureDependencyTreeMapper;
import com.graphinsight.indicator.auto.service.IComplexMeasureDependencyTreeService;
import org.springframework.stereotype.Service;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author lixiaolong5
 * @since 2022-02-17
 */
@Service
@DS("mysql")
public class ComplexMeasureDependencyTreeServiceImpl extends ServiceImpl<ComplexMeasureDependencyTreeMapper, ComplexMeasureDependencyTree> implements IComplexMeasureDependencyTreeService {

}
