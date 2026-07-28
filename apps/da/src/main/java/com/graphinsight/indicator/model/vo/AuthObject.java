package com.graphinsight.indicator.model.vo;

import com.graphinsight.indicator.enums.IndicatorAuthObjectType;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

/**
 * Date: 2022/11/28
 * Desc: 授权对象
 */
@Data
public class AuthObject {

    /**
     * 授权对象类型
     */
    @ApiModelProperty(value = "授权对象类型 0-飞书架构 1-员工 2-运营架构 3-岗位")
    private IndicatorAuthObjectType authObjectType;

    /**
     * 授权对象唯一标识
     */
    @ApiModelProperty(value = "授权对象唯一标识")
    private String code;

    @ApiModelProperty(value = "授权部门全名 回显时用")
    private String namepath;


    /**
     * 对象名称
     */
    @ApiModelProperty(value = "授权对象名称 回显时用")
    private String name;

    /**
     * 部门用户数
     */
    @ApiModelProperty(value = "授权对象名称 部门人数，回显时用")
    private Integer userNum;

    /**
     * 头像
     */
    @ApiModelProperty(value = "头像")
    private String avatar;


    /**
     * 空间ID
     */
    private Long spaceId;
}
