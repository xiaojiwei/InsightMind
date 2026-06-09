package com.graphinsight.indicator.auto.service.impl;

import com.baomidou.dynamic.datasource.annotation.DS;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.graphinsight.indicator.auto.entity.DimensionApplication;
import com.graphinsight.indicator.auto.mapper.DimensionApplicationMapper;
import com.graphinsight.indicator.auto.service.IDimensionApplicationService;
import org.springframework.stereotype.Service;

/**
 * <p>
 * 维度应用表 服务实现类
 * </p>
 *
 * @author lixiaolong
 * @since 2021-11-16
 */
@Service
@DS("mysql")
public class DimensionApplicationServiceImpl extends ServiceImpl<DimensionApplicationMapper, DimensionApplication> implements IDimensionApplicationService {

}
