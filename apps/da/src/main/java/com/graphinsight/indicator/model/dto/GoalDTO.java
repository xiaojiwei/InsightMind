package com.graphinsight.indicator.model.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Data
public class GoalDTO {

    private Long id;

    private Long parentId;

    private String goalName;

    private Integer dimViewType;

    private String dateType;

    private String dateValue;

    private String dateDimCode;

    private String dateValueId;

    private String dimensionCode;

    private String dimensionValue;

    private String dimensionValueId;

    private String measureCode;

    private String targetNum;

    private Long spaceId;

    private LocalDate periodStart;

    private LocalDate periodEnd;

    private String aggregationType;

    private String favorableDirection;

    private BigDecimal lowerBound;

    private BigDecimal upperBound;

    private String calendarCode;

    private String filtersJson;

    private String timezone;

    private Boolean forecastEnabled;

    private Integer seasonalPeriod;

    private Boolean validate;

    private String realNum;

    private Integer diffRateAlgo;

    private String achieveRate;

    private Integer status;

    private String diff;

    private String diffRate;

    private String contribute;

    private String remark;

    private List<GoalDTO> children;
}
