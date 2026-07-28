package com.graphinsight.indicator.model.vo;

import com.graphinsight.indicator.auto.entity.MeasureMonitorConfigDesc;
import com.graphinsight.indicator.auto.entity.User;
import com.graphinsight.indicator.model.feishu.ChatGroup;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

import javax.validation.constraints.NotEmpty;
import javax.validation.constraints.NotNull;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

/**
 * Date: 2022/10/11
 * Desc:
 */
@Data
@ApiModel(value = "MeasureMonitor", description = "创建预警参数")
public class MeasureMonitorVO {

    /**
     * 主键
     */
    private Long id;

    /**
     * 名称
     */
    @NotNull(message = "预警名称不能为空")
    @ApiModelProperty(value = "预警名称")
    private String name;

    /**
     * 预警级别
     */
    private Integer level;

    /**
     * 状态 0-停用 1-启用
     */
    @ApiModelProperty(value = "状态 0-停用 1-启用")
    private Integer status = 0;

    /**
     * 累计触发次数
     */
    @ApiModelProperty(value = "累计触发次数")
    private Integer triggerCount;

    /**
     * 创建时间
     */
    private Long createTime;

    /**
     * 更新时间
     */
    private Long updateTime;

    /**
     * 创建人
     */
    private User creator;

    /**
     * 更新人
     */
    private User updater;

    /**
     * 发送时间cron表达式
     */
    private String cron;



    @NotNull(message = "监控推送时间不能为空")
    @ApiModelProperty(value = "监控推送时间")
    private TaskScheduleVO taskSchedule;

    private String cronDesc;

    @NotNull(message = "告警模版不能为空")
    @ApiModelProperty(value = "告警模版")
    private String alertContent;

    /*
    * 告警接收群
     */
    @ApiModelProperty(value = "告警接收群")
    private List<ChatGroup> receiveChatGroup;

    /**
     * 告警接收人
     */
    @ApiModelProperty(value = "告警接收人")
    private List<User> receiver = new ArrayList<>();

    /**
     * 告警规则
     */
    @NotEmpty(message = "规则列表不能为空")
    @ApiModelProperty(value = "预警规则")
    private List<MeasureMonitorRuleVO> rules;

    /**
     * 空间ID
     */
    @NotNull(message = "空间ID不能为空")
    private Long spaceId;

    /**
     * 上次触发时间
     */
    private Long lastTriggerTime;

    /**
     * 配置解读
     */
    private List<MeasureMonitorConfigDesc> measureMonitorConfigDescList;

    /**
     * 0-AND 1-OR
     */
    @NotNull(message = "逻辑关系逻辑关系不能为空")
    private Integer logicType;
}
