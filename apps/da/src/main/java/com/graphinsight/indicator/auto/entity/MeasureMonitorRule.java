package com.graphinsight.indicator.auto.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serializable;

/**
 * <p>
 * 监控规则
 * </p>
 *
 * @since 2022-10-11
 */
@Data
@EqualsAndHashCode(callSuper = false)
public class MeasureMonitorRule implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 主键
     */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /**
     * 预警表主键
     */
    private Long monitorId;

    /**
     * 0-AND 1-OR
     */
    private Integer logicType;

    /**
     * 次序
     */
    private Integer seq;


    /**
     * 父ID
     */
    private Long parentId;

    /**
     * 优先级
     */
    private Integer level;



}
