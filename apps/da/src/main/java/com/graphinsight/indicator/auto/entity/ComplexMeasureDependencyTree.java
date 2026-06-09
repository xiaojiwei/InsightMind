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
 * @since 2022-02-17
 */
@Data
@EqualsAndHashCode(callSuper = false)
public class ComplexMeasureDependencyTree implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 主键
     */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /**
     * 计算指标Code
     */
    private Integer complexMeasId;

    /**
     * 依赖的code
     */
    private Integer dependencyId;

    /**
     * 1-指标 2-维度
     */
    private Integer dependencyType;

    /**
     * 指标依赖的指标应用表主键
     */
    private Integer dependencyMeasAppId;


    /**
     * 指标应用表主键
     */
    private Integer measAppId;


}
