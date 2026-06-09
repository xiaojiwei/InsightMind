package com.graphinsight.indicator.model.dto;

import lombok.Data;

import java.math.BigDecimal;
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

    private String measureCode;

    private String targetNum;

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

