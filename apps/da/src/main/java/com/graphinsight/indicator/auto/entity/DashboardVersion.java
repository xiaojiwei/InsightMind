package com.graphinsight.indicator.auto.entity;

import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * <p>
 * 看板版本表
 * </p>
 *
 * @author lixiaolong5
 * @since 2022-08-31
 */
@Data
@EqualsAndHashCode(callSuper = false)
public class DashboardVersion extends BaseEntityV2 implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 主键
     */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /**
     * 看板ID
     */
    private Long dashboardId;

    /**
     * 备注
     */
    private String remark;


    /**
     * 所属文件夹id
     */
    @TableField(updateStrategy = FieldStrategy.IGNORED)
    private Long folderId;

    /**
     * 发布人
     */
    private String publisher;

    /**
     * 看板名称
     */
    private String name;

    /**
     * 发布时间
     */
    private LocalDateTime publishTime;

}
