package com.graphinsight.indicator.auto.service.impl;

import com.baomidou.dynamic.datasource.annotation.DS;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.graphinsight.indicator.auto.entity.DimensionFilter;
import com.graphinsight.indicator.auto.mapper.DimensionFilterMapper;
import com.graphinsight.indicator.auto.service.IDimensionFilterService;
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
public class DimensionFilterServiceImpl extends ServiceImpl<DimensionFilterMapper, DimensionFilter> implements IDimensionFilterService {

}
