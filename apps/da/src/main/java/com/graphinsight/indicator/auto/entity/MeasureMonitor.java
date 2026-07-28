package com.graphinsight.indicator.auto.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import lombok.Data;
import lombok.EqualsAndHashCode;

import javax.validation.constraints.NotNull;
import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.List;

/**
 * <p>
 * 指标预警表
 * </p>
 *
 * @since 2022-10-11
 */
@Data
@EqualsAndHashCode(callSuper = false)
public class MeasureMonitor extends BaseEntityV2 implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 主键
     */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /**
     * 名称
     */
    private String name;

    /**
     * 预警级别
     */
    private Integer level;

    /**
     * 状态 0-停用 1-启用
     */
    private Integer status;

    /**
     * 累计触发次数
     */
    private Integer triggerCount;

    /**
     * 发送时间cron表达式
     */
    private String cron;

    private String taskSchedule;

    private String cronDesc;

    private String alertContent;

    /**
     * 告警接收人
     */
    private String receiver;

    /**
     * 空间ID
     */
    private Long spaceId;

    /**
     * cron表达式类型
     */
    private Integer cronType;

    /**
     * 上次触发时间
     */
    private LocalDateTime lastTriggerTime;

    /**
     * 0-AND 1-OR
     */
    @NotNull(message = "逻辑关系逻辑关系不能为空")
    private Integer logicType;
}
