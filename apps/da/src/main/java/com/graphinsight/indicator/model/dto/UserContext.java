package com.graphinsight.indicator.model.dto;

import com.graphinsight.indicator.auto.entity.Dimension;
import com.graphinsight.indicator.auto.entity.Measure;
import com.graphinsight.indicator.auto.entity.User;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Author: lixiaolong
 * Date: 2022/5/19
 * Desc:
 */
@Data
public class UserContext {
    /**
     * 有查询权限的指标
     */
    private List<Measure> authMeasures = new ArrayList<>();
    /**
     * 配置了维值过滤的维度(行级权限限制)
     */
    private List<Dimension> dimensionsWithFilter = new ArrayList<>();
    /**
     * 用户的基本信息
     */
    private User user;
    /**
     * 空间ID
     */
    private Long spaceId;

    /**
     * 运营架构授权类型下的维度值
     * key：operateGrantConfigId 运营架构授权配置表(operate_grant_config)主键
     */
    private Map<Long,OperateGrantValue> operateGrantValueMap;

    /**
     * 是否是超级管理员
     */
    private boolean isSuperAdmin = false;


}
