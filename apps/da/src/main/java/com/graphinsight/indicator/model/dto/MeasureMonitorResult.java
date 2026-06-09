package com.graphinsight.indicator.model.dto;

import com.graphinsight.indicator.auto.entity.Dimension;
import com.graphinsight.indicator.auto.entity.Measure;
import com.graphinsight.indicator.auto.entity.MeasureMonitor;
import com.graphinsight.indicator.enums.CompareWayEnum;
import com.graphinsight.indicator.enums.IndicatorRatioType;
import com.graphinsight.indicator.enums.StatPeriodEnum;
import com.graphinsight.indicator.model.Filter;
import lombok.Data;

import java.util.List;

/**
 * Author: lixiaolong
 * Date: 2022/10/12
 * Desc:
 */
@Data
public class MeasureMonitorResult {

    private Boolean trigger = Boolean.FALSE;

    private Measure measure;

    private Dimension dimension;

    private String thresholdValue;

    private CompareWayEnum compareWayEnum;

    private StatPeriodEnum statPeriodEnum;

    private IndicatorRatioType ratioType;

    private List<MeasureMonitorDimGroupQueryResult> results;

    private List<Filter> filters;

    private Integer level;

    private Long ruleId;
}
