package com.graphinsight.indicator.auto.service.impl;

import com.baomidou.dynamic.datasource.annotation.DS;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.graphinsight.indicator.auto.entity.DimensionOperator;
import com.graphinsight.indicator.auto.mapper.DimensionOperatorMapper;
import com.graphinsight.indicator.auto.service.IDimensionOperatorService;
import org.springframework.stereotype.Service;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @since 2022-02-11
 */
@Service
@DS("mysql")
public class DimensionOperatorServiceImpl extends ServiceImpl<DimensionOperatorMapper, DimensionOperator> implements IDimensionOperatorService {

}
