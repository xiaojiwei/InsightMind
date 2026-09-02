package com.graphinsight.indicator.model.vo;

import com.graphinsight.indicator.auto.entity.User;
import com.graphinsight.indicator.enums.IndicatorAuthType;
import lombok.Data;

import javax.validation.constraints.NotNull;
import java.util.List;

/**
 * Date: 2022/10/24
 * Desc:
 */
@Data
public class PortalVO {

    /**
     * 主键
     */
    private Long id;

    /**
     * 门户名称
     */
    @NotNull(message = "名称不能为空")
    private String name;

    /**
     * 空间ID
     */
    @NotNull(message = "空间ID不能为空")
    private Long spaceId;

    /**
     * 创建人
     */
    private User creator;

    /**
     * 更新人
     */
    private User updater;

    /**
     * 创建时间
     */
    private Long createTime;

    /**
     * 更新时间
     */
    private Long updateTime;

    /**
     * 状态 0-下线 1-上线
     */
    private Integer status;

    /**
     * 备注
     */
    private String remark;

    /**
     * url
     */
    private String url;

    /**
     * 编码
     */
    private String code;


    /**
     * 菜单
     */
    private List<TreeNode<PortalMenuVO>> children;

    /**
     * 菜单
     */
    private List<PortalMenuVO> menus;

    /**
     * 权限类型
     */
    private List<IndicatorAuthType> authTypes;

    private List<CustomerVo> customers;

    private String msg;

    private int open = 0;
}
