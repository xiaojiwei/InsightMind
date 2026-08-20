package com.graphinsight.indicator.auto.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.extension.activerecord.Model;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Data
public class Goal extends Model<Goal> {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;


    private Long spaceId;

    private String measureCode;


    private String dimensionCode;


    private String dimensionValue;

    private String dimensionValueId;

    private Integer dimViewType;

    private Long parentId;

    private BigDecimal targetNum;

    private BigDecimal realNum;

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

    private Integer diffRateAlgo;

    private Integer status;

    private String remark;

    private String creator;

    private String updater;

    private LocalDateTime createTime;

    @TableField(exist = false)
    private List<Goal> children;

}
