package com.graphinsight.indicator.auto.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import java.time.LocalDateTime;
import java.io.Serializable;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * <p>
 * 属权元素指标或维度
 * </p>
 *
 * @author lixiaolong5
 * @since 2023-02-13
 */
@Data
@EqualsAndHashCode(callSuper = false)
public class AuthElement implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 唯一主键
     */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /**
     * cobe状态下的唯一标识
     */
    private String code;

    /**
     * 创建时间
     */
    private LocalDateTime createDate;

    /**
     * 创建人
     */
    private String creator;

    /**
     * 修改时间
     */
    private LocalDateTime updateDate;

    /**
     * 修改人
     */
    private String updater;

    /**
     * 唯一主键
     */
    private Long authId;

    /**
     * 唯一主键
     */
    private Long filterId;

    /**
     * 授权类型
     */
    private Long authElementType;

    /**
     * 标准模式、上下文模式
     */
    private Long authFilterParamType;


}
