package com.graphinsight.indicator.auto.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.graphinsight.indicator.auto.entity.MeasureMonitorConfigDesc;
import com.graphinsight.indicator.auto.mapper.MeasureMonitorConfigDescMapper;
import com.graphinsight.indicator.auto.service.IMeasureMonitorConfigDescService;
import org.springframework.stereotype.Service;

@Service
public class MeasureMonitorConfigDescServiceImpl extends ServiceImpl<MeasureMonitorConfigDescMapper, MeasureMonitorConfigDesc> implements IMeasureMonitorConfigDescService {
}
