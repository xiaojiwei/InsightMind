package com.graphinsight.indicator.model.vo;

import com.graphinsight.indicator.auto.entity.User;
import lombok.Data;

/**
 * Author: lixiaolong
 * Date: 2022/8/31
 * Desc:
 */
@Data
public class DashboardFolderVO extends BaseVO {

    Long spaceId;

    String name;

    Long id;

    User creator;

    User updater;

    Long createTime;

    Long updateTime;

    Long parentId;




}
