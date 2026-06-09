package com.graphinsight.indicator.auto.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * <p>
 * 用户操作记录表
 * </p>
 *
 * @author lixiaolong5
 * @since 2023-02-09
 */
@Data
@EqualsAndHashCode(callSuper = false)
public class UserAuditLog implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 主键
     */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /**
     * 操作用户
     */
    private String username;

    /**
     * ip
     */
    private String ip;

    /**
     * 请求路径
     */
    private String url;

    /**
     * 请求方法
     */
    private String method;

    /**
     * 请求参数
     */
    private String param;

    /**
     * 请求时间
     */
    private LocalDateTime time;

    /**
     * 链路id
     */
    private String traceId;


}
