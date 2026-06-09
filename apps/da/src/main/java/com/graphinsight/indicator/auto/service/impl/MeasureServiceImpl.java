package com.graphinsight.indicator.auto.service.impl;

import com.baomidou.dynamic.datasource.annotation.DS;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.graphinsight.indicator.auto.entity.Measure;
import com.graphinsight.indicator.auto.mapper.MeasureMapper;
import com.graphinsight.indicator.auto.service.IMeasureService;
import org.springframework.stereotype.Service;

/**
 * <p>
 * 指标表 服务实现类
 * </p>
 *
 * @author lixiaolong
 * @since 2021-11-16
 */
@Service
@DS("mysql")
public class MeasureServiceImpl extends ServiceImpl<MeasureMapper, Measure> implements IMeasureService {

}
