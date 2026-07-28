package com.graphinsight.indicator.auto.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serializable;

/**
 * <p>
 * 菜单表
 * </p>
 *
 * @since 2022-10-24
 */
@Data
@EqualsAndHashCode(callSuper = false)
public class PortalMenu implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 主键
     */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /**
     * 名称
     */
    private String name;

    /**
     * 门户ID
     */
    private Long portalId;

    /**
     * 父级菜单id
     */
    private Long parentId;

    /**
     * 顺序
     */
    private Integer seq;

    /**
     * 内容类型 0-看板 1-外链
     */
    private Integer contentType;

    /**
     * 内容
     */
    private String content;

    /**
     * 编码
     */
    private String code;


}
