package com.graphinsight.indicator.model.vo;

import lombok.Data;

import javax.validation.constraints.NotNull;

@Data
public class CustomerVo {
    /**
     * 主键
     */
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
