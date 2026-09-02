package com.graphinsight.indicator.auto.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.graphinsight.indicator.model.DimWithValues;
import com.graphinsight.indicator.model.Filter;
import lombok.Data;
import lombok.EqualsAndHashCode;

import javax.validation.constraints.NotNull;
import java.io.Serializable;
import java.util.List;

/**
 * <p>
 * 告警详情
 * </p>
 *
 * @since 2022-10-11
 */
@Data
@EqualsAndHashCode(callSuper = false)
public class MeasureMonitorRuleDetail implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 主键
     */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /**
     * 规则ID
     */
    private Long ruleId;

    /**
     * 0-AND 1-OR
     */
    private Integer logicType;

    /**
     * 指标code
     */
    private String measCode;

    /**
     * 时间维度code
     */
    private String dimCode;


    private Integer statPeriod;

    /**
     * 同环比类型参考枚举类 RatioType
     */
    private Integer ratioType;

    /**
     * 阈值类型0-固定值 1-目标值 2-预测值
     */
    private Integer thresholdType;

    /**
     * 阈值
     */
    private String thresholdValue;

    /**
     * 比较方式
     */
    private Integer compareWay;

    /**
     * 父ID
     */
    private Long parentId;

    /**
     * 顺序
     */
    private Integer seq;


}
