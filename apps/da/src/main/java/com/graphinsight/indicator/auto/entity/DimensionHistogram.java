package com.graphinsight.indicator.auto.entity;

import com.baomidou.mybatisplus.annotation.IdType;
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
 * @since 2023-01-04
 */
@Data
@EqualsAndHashCode(callSuper = false)
public class DimensionHistogram implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 主键
     */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /**
     * 维度code
     */
    private String dimCode;

    /**
     * 表名
     */
    private String tableName;

    /**
     * 行数
     */
    private Long rowNum;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;


}
