package com.graphinsight.indicator.auto.service.impl;

import com.baomidou.dynamic.datasource.annotation.DS;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.graphinsight.indicator.auto.entity.Dimension;
import com.graphinsight.indicator.auto.mapper.DimensionMapper;
import com.graphinsight.indicator.auto.service.IDimensionService;
import org.springframework.stereotype.Service;

/**
 * <p>
 * 维度表 服务实现类
 * </p>
 *
 * @author lixiaolong
 * @since 2021-11-16
 */
@Service
@DS("mysql")
public class DimensionServiceImpl extends ServiceImpl<DimensionMapper, Dimension> implements IDimensionService {

}
