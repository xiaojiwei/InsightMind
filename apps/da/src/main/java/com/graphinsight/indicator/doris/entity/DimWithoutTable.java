package com.graphinsight.indicator.doris.entity;

import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serializable;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Date: 2022/2/15
 * Desc:
 */
@Data
@EqualsAndHashCode(callSuper = false)
public class DimWithoutTable implements Serializable {
    private static final long serialVersionUID = 1L;

    /**
     * 主键
     */
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
