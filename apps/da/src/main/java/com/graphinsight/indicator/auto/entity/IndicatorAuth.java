package com.graphinsight.indicator.auto.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serializable;

/**
 * <p>
 * 授权表
 * </p>
 *
 * @since 2022-11-28
 */
@Data
@EqualsAndHashCode(callSuper = false)
public class IndicatorAuth extends BaseEntityV2 implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 主键
     */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /**
     * 授权对象唯一标识
     */
    private String objCode;

    /**
     * 授权对象类型 0-用户 1-飞书架构 2-运营架构
     */
    private Integer objType;

    /**
     * 权限类型 0-浏览 1-导出 2-编辑 3-管理
     */
    private String authType;

    /**
     * 模块类型 0-门户 1-看板 
     */
    private Integer moduleType;

    /**
     * 业务类型，各个模块自己定义。对门户 0-门户 1-菜单
     */
    private Integer bizType;

    /**
     * 授权元素的标识
     */
    private String elementCode;

    /**
     * 空间ID
     */
    private Long spaceId;


}
