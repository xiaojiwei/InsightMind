package com.graphinsight.indicator.auto.entity;

import lombok.Data;

/**
 * Table: measure_relate_recode
 */
@Data
public class MeasureRelateRecode {
    /**
     * Column: id
     * Type: BIGINT UNSIGNED
     * Remark: 唯一主键
     */
    private Long id;

    /**
     * Column: m_code
     * Type: VARCHAR(255)
     * Remark: 指标编码
     */
    private String mCode;

    /**
     * Column: r_code
     * Type: VARCHAR(255)
     * Remark: 相关的编码
     */
    private String rCode;

    /**
     * Column: m_data
     * Type: VARCHAR(255)
     * Remark: 计算的相关值
     */
    private String mData;

    /**
     * Column: m_type
     * Type: VARCHAR(255)
     * Remark: positive 正相关 negative 负相关 no 不相关
     */
    private String mType;

    /**
     * Column: dim_type
     * Type: VARCHAR(255)
     * Remark: 按维度类型查看类型
     */
    private String dimType;

}