package com.graphinsight.indicator.model.dto;

import com.graphinsight.indicator.auto.entity.Dimension;
import com.graphinsight.indicator.auto.entity.Measure;
import com.graphinsight.indicator.auto.entity.MeasureMonitorRule;
import com.graphinsight.indicator.enums.CompareWayEnum;
import com.graphinsight.indicator.enums.IndicatorRatioType;
import com.graphinsight.indicator.enums.StatPeriodEnum;
import com.graphinsight.indicator.model.Filter;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

/**
 * Date: 2022/10/12
 * Desc:
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class MeasureMonitorRuleQueryResult {

    private Boolean trigger = Boolean.FALSE;

    private Measure measure;

    private Dimension dimension;

    private String thresholdValue;

    private List<MeasureMonitorDimGroupQueryResult> results;

    private List<Filter> filters;

    private CompareWayEnum compareWayEnum;

    private IndicatorRatioType ratioType;

    private StatPeriodEnum statPeriodEnum;

    private MeasureMonitorRule rule;

    private Long parentRuleId;
}
