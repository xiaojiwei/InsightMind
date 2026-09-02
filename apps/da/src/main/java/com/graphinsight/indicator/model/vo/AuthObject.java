package com.graphinsight.indicator.model.vo;

import com.graphinsight.indicator.enums.IndicatorAuthObjectType;
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
    private IndicatorAuthObjectType authObjectType;

    /**
     * 授权对象唯一标识
     */
    private String code;

    private String namepath;


    /**
     * 对象名称
     */
    private String name;

    /**
     * 部门用户数
     */
    private Integer userNum;

    /**
     * 头像
     */
    private String avatar;


    /**
     * 空间ID
     */
    private Long spaceId;
}
