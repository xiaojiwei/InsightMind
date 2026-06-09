package com.graphinsight.indicator.auto.entity;

import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serializable;

/**
 * <p>
 * 告警接收人
 * </p>
 *
 * @author lixiaolong5
 * @since 2022-10-17
 */
@Data
@EqualsAndHashCode(callSuper = false)
public class MeasureMonitorReceiver implements Serializable {

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
     * 接收人code
     */
    private String receiverCode;

    /**
     * 接收人类型 0-个人 1-组织 2-飞书群
     */
    private Integer receiverType;

    /**
     * 接收人名称或部门名称
     */
    private String receiverName;

    /**
     * 是否已发送提醒 0-否 1-是
     */
    private Integer sendTips;

    /**
     * 是否发送飞书 0-否 1-是
     */
    private Integer sendFeishu;

    /**
     * 是否发送邮件 0-否 1-是
     */
    private Integer sendEmail;

    private String avatar;

    private String description;

}
