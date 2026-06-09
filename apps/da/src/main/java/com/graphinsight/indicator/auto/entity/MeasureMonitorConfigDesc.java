package com.graphinsight.indicator.auto.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import lombok.Data;

@Data
public class MeasureMonitorConfigDesc {
    /**
     * 主键
     */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /**
     * 指标名称
     */
    private String measure;

    /**
     * 解读来源
     */
    private String source;

    /**
     * 拆解树名称
     */
    private String dismantlingTree;

    /**
     * 预警表主键
     */
    private Long monitorId;
}
