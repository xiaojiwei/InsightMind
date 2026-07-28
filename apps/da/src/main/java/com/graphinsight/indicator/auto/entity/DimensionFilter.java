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
 * @since 2022-02-11
 */
@Data
@EqualsAndHashCode(callSuper = false)
public class DimensionFilter implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 主键
     */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /**
     * 指标应用表主键
     */
    private Integer measAppId;

    /**
     * 维度code
     */
    private String dimCode;

    /**
     * 多个维度关联类型 1-and 2-or
     */
    private Integer sqlLogicalType;

    /**
     * 顺序
     */
    private Integer seq;

}
