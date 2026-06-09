package com.graphinsight.indicator.auto.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import java.time.LocalDateTime;
import java.io.Serializable;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * <p>
 * 指标分类
 * </p>
 *
 * @author lixiaolong5
 * @since 2022-05-12
 */
@Data
@EqualsAndHashCode(callSuper = false)
public class Classification implements Serializable {

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
     * 指标标识
     */
    private String cCode;

    /**
     * 名称
     */
    private String name;

    /**
     * 唯一主键
     */
    private Long spaceId;


}
