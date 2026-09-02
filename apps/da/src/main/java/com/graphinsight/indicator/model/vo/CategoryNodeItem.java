package com.graphinsight.indicator.model.vo;

import com.graphinsight.indicator.auto.entity.User;
import lombok.Data;

/**
 * Date: 2022/3/9
 * Desc:
 */
@Data
public class CategoryNodeItem {

    private Integer id;

    private String cnName;

    private String enName;

    public String code;

    public Integer viewType;

    public String description;

    public Integer leafCategoryId;

    private String type;

    private Integer parentId;

    private Integer dimValueCount;

    private Integer sequence;

    private Integer hierarchyId;
    private Integer levelSequence;

    private Integer online;

    private String offlineReason;

    /**
     * 是否属于空间
     */
    private boolean belongSpace = true;

    /**
     * 用户是否有权限
     */
    private boolean hasAuth = true;

    private User mangerInfo;

    private String functionType;
    private String expression;

}
