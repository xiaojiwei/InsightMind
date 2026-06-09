package com.graphinsight.indicator.model.vo;

import com.graphinsight.indicator.auto.entity.User;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * Author: lixiaolong
 * Date: 2022/9/1
 * Desc:
 */
@Data
public class DashboardVO extends DashboardFolderVO{

    private Long id;

    private String name;

    /**
     * 文件夹ID
     */
    private Long folderId;

    /**
     * 看板状态
     */
    private Integer status;

    /**
     * 在线版本号
     */
    private Long onlineVersionId;

    /**
     * 草稿版本号
     */
    private Long latestVersionId;

    /**
     * 发布版本ID
     */
    private Long publishVersionId;

    /**
     * 历史所有版本
     */
    private List<Long> versionIds;

    /**
     * 组件列表
     */
    private List<WidgetVO> widgets = new ArrayList<>();

    /**
     * 看板发布人
     */
    private User publisher;

    /**
     * 发布时间
     */
    private Long publishTime;

    /**
     * 备注
     */
    private String remark;


}