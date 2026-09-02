package com.graphinsight.indicator.model.vo;

import com.graphinsight.indicator.enums.OrganizationType;
import lombok.Data;

import javax.validation.constraints.NotNull;

/**
 * Date: 2022/5/25
 * Desc:
 */
@Data
public class OrganizationQueryVO extends BaseVO {

    @NotNull(message = "架构类型不能为空")
    private Integer orgType;

    private  String searchText;
}
