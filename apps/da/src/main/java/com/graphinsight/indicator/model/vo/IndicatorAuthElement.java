package com.graphinsight.indicator.model.vo;

import com.graphinsight.indicator.enums.AuthBizType;
import com.graphinsight.indicator.enums.AuthMoudleType;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

import javax.validation.constraints.NotNull;

/**
 * Date: 2022/11/28
 * Desc:
 */
@Data
public class IndicatorAuthElement {

    /**
     * 授权资源唯一标识
     */
    @NotNull(message = "资源唯一标识不能为空")
    @ApiModelProperty(value = "资源唯一标识")
    private String elementCode;

    /**
     * 模块类型
     */
    @NotNull(message = "模块类型不能为空")
    @ApiModelProperty(value = "模块类型 0-门户 1-看板")
    private AuthMoudleType moduleType;

    /**
     * 业务类型
     */
    @ApiModelProperty(value = "模块类型 0-门户 1-菜单")
    private AuthBizType bizType;

    /**
     * 空间ID
     */
    private Long spaceId;
}
