package com.graphinsight.indicator.auto.service.impl;

import com.baomidou.dynamic.datasource.annotation.DS;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.graphinsight.indicator.auto.entity.DimensionValues;
import com.graphinsight.indicator.auto.mapper.DimensionValuesMapper;
import com.graphinsight.indicator.auto.service.IDimensionValuesService;
import org.springframework.stereotype.Service;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author lixiaolong5
 * @since 2022-02-15
 */
@Service
@DS("mysql")
public class DimensionValuesServiceImpl extends ServiceImpl<DimensionValuesMapper, DimensionValues> implements IDimensionValuesService {

}
