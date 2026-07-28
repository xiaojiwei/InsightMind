package com.graphinsight.indicator.model.vo;

import com.graphinsight.indicator.auto.entity.User;
import com.graphinsight.indicator.enums.IndicatorAuthType;
import io.swagger.annotations.ApiModelProperty;
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
    @ApiModelProperty(value = "主键 更新时传")
    private Long id;

    /**
     * 门户名称
     */
    @NotNull(message = "名称不能为空")
    @ApiModelProperty(value = "名称",required = true)
    private String name;

    /**
     * 空间ID
     */
    @NotNull(message = "空间ID不能为空")
    @ApiModelProperty(value = "空间ID", required = true)
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
    @ApiModelProperty(value = "菜单的树形结构，回显时用")
    private List<TreeNode<PortalMenuVO>> children;

    /**
     * 菜单
     */
    @ApiModelProperty(value = "菜单的树形结构，保存时用")
    private List<PortalMenuVO> menus;

    /**
     * 权限类型
     */
    private List<IndicatorAuthType> authTypes;

    @ApiModelProperty(value = "客服助手添加用户")
    private List<CustomerVo> customers;

    @ApiModelProperty(value = "群发送消息")
    private String msg;

    @ApiModelProperty(value = "客服助手开启")
    private int open = 0;
}
