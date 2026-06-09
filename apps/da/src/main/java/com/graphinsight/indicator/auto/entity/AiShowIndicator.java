package com.graphinsight.indicator.auto.entity;

import lombok.Data;

import java.io.Serializable;
import java.util.Date;

/**
 * Table: ai_show_indicator
 */
@Data
public class AiShowIndicator implements Serializable {
    private static final long serialVersionUID = 1L;
    /**
     * Column: id
     * Type: BIGINT
     * Remark: 主键
     */
    private Integer id;

    /**
     * Column: m_code
     * Type: VARCHAR(255)
     * Remark: 指标编码，展示用，由平台生成，全局唯一，比如meas_xxx，代表这个指标
     */
    private String mCode;

    /**
     * Column: cn_name
     * Type: VARCHAR(255)
     * Remark: 指标中文名，全局唯一
     */
    private String cnName;

    /**
     * Column: is_del
     * Type: TINYINT(3)
     * Default value: 0
     * Remark: 是否删除 0否 1是
     */
    private Integer isDel;

    /**
     * Column: create_time
     * Type: TIMESTAMP
     * Default value: CURRENT_TIMESTAMP
     * Remark: 创建时间
     */
    private Date createTime;

    /**
     * Column: update_time
     * Type: TIMESTAMP
     * Default value: CURRENT_TIMESTAMP
     * Remark: 更新时间
     */
    private Date updateTime;
}