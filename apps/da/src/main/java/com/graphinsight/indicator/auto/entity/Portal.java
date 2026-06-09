package com.graphinsight.indicator.auto.entity;

import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serializable;
import java.util.List;

/**
 * <p>
 * 门户表
 * </p>
 *
 * @author lixiaolong5
 * @since 2022-10-24
 */
@Data
@EqualsAndHashCode(callSuper = false)
public class Portal extends BaseEntityV2 implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 主键
     */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /**
     * 门户名称
     */
    private String name;

    /**
     * 空间ID
     */
    private Long spaceId;

    /**
     * 状态 0-下线 1-上线
     */
    private Integer status;

    /**
     * 是否删除 0-否 1-是
     */
    private Integer isDelete;

    /**
     * 备注
     */
    private String remark;

    /**
     * url
     */
    @TableField(updateStrategy = FieldStrategy.IGNORED)
    private String url;

    /**
     * 编码
     */
    private String code;

    @ApiModelProperty(value = "群发送消息")
    private String msg;

    @ApiModelProperty(value = "客服助手开启")
    private int open = 0;


}
