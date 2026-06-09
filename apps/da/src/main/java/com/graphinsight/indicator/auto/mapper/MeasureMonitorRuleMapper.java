package com.graphinsight.indicator.auto.mapper;

import com.baomidou.dynamic.datasource.annotation.DS;
import com.graphinsight.indicator.auto.entity.MeasureMonitorRule;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;

/**
 * <p>
 * 监控规则 Mapper 接口
 * </p>
 *
 * @author lixiaolong5
 * @since 2022-10-11
 */
@DS("mysql")
public interface MeasureMonitorRuleMapper extends BaseMapper<MeasureMonitorRule> {

}
