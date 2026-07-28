package com.graphinsight.indicator.auto.service.impl;

import com.baomidou.dynamic.datasource.annotation.DS;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.graphinsight.indicator.auto.entity.DimensionOperatorValue;
import com.graphinsight.indicator.auto.mapper.DimensionOperatorValueMapper;
import com.graphinsight.indicator.auto.service.IDimensionOperatorValueService;
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
public class DimensionOperatorValueServiceImpl extends ServiceImpl<DimensionOperatorValueMapper, DimensionOperatorValue> implements IDimensionOperatorValueService {

}
