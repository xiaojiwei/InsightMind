package com.graphinsight.indicator.auto.service.impl;

import com.baomidou.dynamic.datasource.annotation.DS;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.graphinsight.indicator.auto.entity.MeasureApplication;
import com.graphinsight.indicator.auto.mapper.MeasureApplicationMapper;
import com.graphinsight.indicator.auto.service.IMeasureApplicationService;
import org.springframework.stereotype.Service;

/**
 * <p>
 * 指标应用表 服务实现类
 * </p>
 *
 * @since 2021-11-16
 */
@Service
@DS("mysql")
public class MeasureApplicationServiceImpl extends ServiceImpl<MeasureApplicationMapper, MeasureApplication> implements IMeasureApplicationService {

}
