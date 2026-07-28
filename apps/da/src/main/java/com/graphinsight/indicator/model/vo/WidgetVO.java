package com.graphinsight.indicator.model.vo;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * Date: 2022/9/1
 * Desc:
 */
@Data
public class WidgetVO {

    /**
     * 主键
     */
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
     * 子组件
     */
    private List<WidgetVO> widgets = new ArrayList<>();

    /**
     * 组件详情
     */
    private List<WidgetDetailVO> details = new ArrayList<>();

    /**
     * 看板唯一标识
     */
    private String code;

    /**
     * 看板ID
     */
    private Long dataSourceId;

}
