package com.graphinsight.indicator.auto.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import lombok.Data;
import lombok.EqualsAndHashCode;

import javax.validation.constraints.NotNull;

@Data
@EqualsAndHashCode(callSuper = false)
public class Customer {
    /**
     * 主键
     */
    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 名称
     */
    @NotNull(message = "用户名称")
    private String name;

    /**
     * 门户ID
     */
    private Long portalId;

    /**
     * 中文名称
     */
    private String nickname;

    /**
     * 头像
     */
    private String avatar;
}
