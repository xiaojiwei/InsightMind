package com.graphinsight.indicator.auto.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serializable;

/**
 * <p>
 * widget表
 * </p>
 *
 * @since 2022-08-31
 */
@Data
@EqualsAndHashCode(callSuper = false)
public class Widget implements Serializable {

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
     * 组件类型0-单图 1-tab 2-tabItem 3-筛选器
     */
    private Integer type;

    /**
     * 前端配置
     */
    private String config;

    /**
     * 父组件id
     */
    private Long parentId;

    /**
     * 备注
     */
    private String remark;

    /**
     * 看板版本ID
     */
    private Long dashboardVersionId;

    /**
     * 看板ID
     */
    private Long dashboardId;

    /**
     * 看板唯一标识
     */
    private String code;

    /**
     * 看板ID
     */
    private Long dataSourceId;


}
