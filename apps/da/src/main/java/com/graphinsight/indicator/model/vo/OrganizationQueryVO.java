package com.graphinsight.indicator.model.vo;

import com.graphinsight.indicator.enums.OrganizationType;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

import javax.validation.constraints.NotNull;

/**
 * Author: lixiaolong
 * Date: 2022/5/25
 * Desc:
 */
@Data
public class OrganizationQueryVO extends BaseVO {

    @NotNull(message = "架构类型不能为空")
    @ApiModelProperty(value = "架构类型 0-飞书 1-运营")
    private Integer orgType;

    @ApiModelProperty(value = "搜索关键字")
    private  String searchText;
}
