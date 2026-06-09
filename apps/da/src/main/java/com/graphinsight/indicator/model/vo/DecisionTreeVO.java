package com.graphinsight.indicator.model.vo;

import com.graphinsight.indicator.annotation.DecisionTreeCreateCheck;
import com.graphinsight.indicator.annotation.DecisionTreeNodeCheck;
import lombok.Data;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;

/**
 * Author: lixiaolong
 * Date: 2022/6/20
 * Desc:
 */
@Data
@DecisionTreeCreateCheck
public class DecisionTreeVO {

    private Long id;

    @NotBlank(message = "树名不能为空")
    private String treeName;

    @NotNull(message = "空间ID不能为空")
    private Long spaceId;

    private Boolean isDefault;

    @DecisionTreeNodeCheck
    private DecisionTreeNode decisionTreeNode;


}
