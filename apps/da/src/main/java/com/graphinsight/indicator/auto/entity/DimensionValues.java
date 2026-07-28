package com.graphinsight.indicator.auto.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serializable;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * <p>
 * 
 * </p>
 *
 * @since 2022-02-15
 */
@Data
@EqualsAndHashCode(callSuper = false)
public class DimensionValues implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 主键
     */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /**
     * 数据灌入日期
     */
    private LocalDate date;

    /**
     * 数据灌入时间戳
     */
    private LocalDateTime timestamp;

    /**
     * key
     */
    private String vKey;

    /**
     * value
     */
    private String vValue;

    /**
     * 维度code
     */
    private String code;


}
