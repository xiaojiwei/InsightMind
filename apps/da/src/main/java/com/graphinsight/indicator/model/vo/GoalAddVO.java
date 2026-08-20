package com.graphinsight.indicator.model.vo;

import com.baomidou.mybatisplus.annotation.TableField;
import com.graphinsight.indicator.enums.ViewType;
import com.graphinsight.indicator.model.BaseDimValue;
import lombok.Data;

import javax.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Data
public class GoalAddVO extends BaseVO {

    @NotNull(message = "空间id不能为空")
    private Long spaceId;

    private Long parentId;

    @NotNull(message = "维度不能为空")
    private String dimensionCode;


    @NotNull(message = "维度展示类型不能为空")
    private Integer dimViewType;

    private List<BaseDimValue> dimensionValueInfo;

    @NotNull(message = "指标不能为空")
    private String measureCode;

    @NotNull(message = "目标值不能为空")
    private String targetNum;

    private LocalDate periodStart;

    private LocalDate periodEnd;

    private String aggregationType = "SUM";

    private String favorableDirection = "HIGHER";

    private BigDecimal lowerBound;

    private BigDecimal upperBound;

    private String calendarCode = "NATURAL";

    private String filtersJson;

    private String timezone = "Asia/Shanghai";

    private Boolean forecastEnabled = true;

    private Integer seasonalPeriod;

    private Boolean validate = false;

    private Integer diffRateAlgo;

    private String remark;
}
