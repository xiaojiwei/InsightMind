package com.graphinsight.indicator.model.vo;

import com.graphinsight.indicator.enums.IndicatorAuthType;
import lombok.Data;

import javax.validation.constraints.NotNull;
import java.util.ArrayList;
import java.util.List;

/**
 * Date: 2022/10/24
 * Desc:
 */
@Data
public class PortalMenuVO {

    /**
     * 主键
     */
    private Long id;

    /**
     * 名称
     */
    @NotNull(message = "菜单名称不能为空")
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
    @NotNull(message = "内容类型不能为空")
    private Integer contentType;

    /**
     * 内容
     */
    private String content;

    /**
     * 编码
     */
    private String code;

    /**
     * 子菜单
     */
    private List<PortalMenuVO> children = new ArrayList<>();

    /**
     * 权限类型
     */
    private List<IndicatorAuthType> authTypes;
}
