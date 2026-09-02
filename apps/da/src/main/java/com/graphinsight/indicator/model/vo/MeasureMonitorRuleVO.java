package com.graphinsight.indicator.model.vo;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import lombok.Data;

import javax.validation.constraints.NotNull;
import java.util.ArrayList;
import java.util.List;

/**
 * Date: 2022/10/11
 * Desc:
 */
@Data
public class MeasureMonitorRuleVO {

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
    @NotNull(message = "逻辑关系逻辑关系不能为空")
    private Integer logicType;

    /**
     * 次序
     */
    private Integer seq;

    /**
     * 子规则
     */
    private List<MeasureMonitorRuleVO> children = new ArrayList<>();

    /**
     * 规则详情
     */
    private List<MeasureMonitorRuleDetailVO> details = new ArrayList<>();

    /**
     * 父ID
     */
    private Long parentId;

    /**
     * 预警级别
     */
    private Integer level = 10;
}
