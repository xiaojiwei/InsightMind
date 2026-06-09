package com.graphinsight.indicator.auto.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serializable;

/**
 * <p>
 * 
 * </p>
 *
 * @author lixiaolong5
 * @since 2022-02-11
 */
@Data
@EqualsAndHashCode(callSuper = false)
public class DimensionOperator implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 主键
     */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /**
     * dimension_filter 表主键
     */
    private Long filterId;

    /**
     * 开始日期
     */
    private String begin;

    /**
     * 结束日期
     */
    private String end;

    /**
     * 多个条件之间的关联类型 1-and 2-or
     */
    private Integer sqlLogicalType;

    /**
     * 条件筛选类型 like eq in 等
     */
    private Integer sqlOprType;

    /**
     * 相对时间类型 比如近一年 近一周等
     */
    private Integer timeRange;

    /**
     * 顺序
     */
    private Integer seq;


}
