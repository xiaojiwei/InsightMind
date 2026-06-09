package com.graphinsight.indicator.auto.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import java.time.LocalDateTime;
import java.io.Serializable;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * <p>
 * 超级管理员
 * </p>
 *
 * @author lixiaolong5
 * @since 2022-06-13
 */
@Data
@EqualsAndHashCode(callSuper = false)
public class TSuperAdmin implements Serializable {

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
     * 用户标识
     */
    private String empCode;


}
