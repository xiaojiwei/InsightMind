package com.graphinsight.indicator.auto.entity;

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
 * @since 2022-12-15
 */
@Data
@EqualsAndHashCode(callSuper = false)
public class MeasureSimilarity implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 主键
     */
    private Long id;

    /**
     * 指标code
     */
    private String code;

    /**
     * 数据
     */
    private String data;

    /**
     * 统计开始时间
     */
    private String startTime;

    /**
     * 统计结束日期
     */
    private String entTime;

    /**
     * 指标ID
     */
    private Integer measId;


}
