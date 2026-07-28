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
public class DimensionOperatorValue implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 主键
     */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /**
     * dimension_operator表主键
     */
    private Long operatorId;

    /**
     * 维度值
     */
    private String value;


}
