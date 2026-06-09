package com.graphinsight.indicator.auto.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * <p>
 * 
 * </p>
 *
 * @author lixiaolong5
 * @since 2022-08-23
 */
@Data
@EqualsAndHashCode(callSuper = false)
public class AuditLog implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 主键
     */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /**
     * 操作类型
     */
    private String operateType;

    /**
     * 操作的sql
     */
    @TableField("`sql`")
    private String sql;

    /**
     * 操作的参数
     */
    private String param;

    /**
     * 操作人
     */
    private String username;

    private String traceId;

    /**
     * 操作时间
     */
    private LocalDateTime operateTime;


}
