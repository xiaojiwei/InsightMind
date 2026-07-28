package com.graphinsight.indicator.auto.entity;

import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serializable;

/**
 * <p>
 * 预警发送日志表
 * </p>
 *
 * @since 2022-10-17
 */
@Data
@EqualsAndHashCode(callSuper = false)
public class MeasureMonitorSendLog implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 主键
     */
    private Long id;

    /**
     * 预警ID
     */
    private Long monitorId;

    /**
     * 预警内容
     */
    private String message;

    /**
     * 预警接收人
     */
    private String receivers;

    /**
     * 发送状态 0-成功 1-失败
     */
    private Integer status;

    /**
     * 发送失败原因
     */
    private String errorMsg;

}
