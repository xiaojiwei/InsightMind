package com.graphinsight.indicator.auto.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serializable;

/**
 * <p>
 * 指标应用表
 * </p>
 *
 * @since 2021-11-16
 */
@Data
@EqualsAndHashCode(callSuper = false)
public class MeasureApplication extends BaseEntity implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 主键
     */
    @TableId(value = "id", type = IdType.AUTO)
    private Integer id;

    /**
     * 指标ID
     */
    private Integer measId;

    /**
     * 0-原生；1-衍生；2-派生
     */
    private Integer applyType;

    /**
     * 原生指标聚合表达式；衍生指标AST表达式；派生指标依赖指标。
     */
    private String expression;

    /**
     * 来源事实表
     */
    private Integer dwTableId;

    /**
     * 来源事实表字段名
     */
    private String factColumn;

    /**
     * 指标在事实表中的字段类型
     */
    private String dataType;

    /**
     * 筛选条件
     */
    private String whereCondition;

    /**
     * 是否可用 0-不可用 1-可用
     */
    private Integer available;

    private String dataFormatStr;

    private Integer decimalPlaces;
    private Integer dataScale;

}
