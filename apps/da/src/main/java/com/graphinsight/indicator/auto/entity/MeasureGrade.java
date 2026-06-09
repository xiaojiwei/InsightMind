package com.graphinsight.indicator.auto.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import java.time.LocalDateTime;
import java.io.Serializable;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * <p>
 * 指标等级字典表
 * </p>
 *
 * @author lixiaolong
 * @since 2021-11-16
 */
@Data
@EqualsAndHashCode(callSuper = false)
public class MeasureGrade implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 主键
     */
    @TableId(value = "id", type = IdType.AUTO)
    private Integer id;

    /**
     * 指标等级,1-N
     */
    private Integer level;

    /**
     * 等级名称
     */
    private String name;

    /**
     * 等级描述
     */
    private String description;

    /**
     * 创建人
     */
    private Integer creator;

    /**
     * 更新人
     */
    private Integer updater;

    /**
     * 创建时间
     */
    private LocalDateTime createTime;

    /**
     * 更新时间
     */
    private LocalDateTime updateTime;


}
