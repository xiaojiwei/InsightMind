package com.graphinsight.indicator.auto.entity;

import lombok.Data;

import java.io.Serializable;
import java.util.Date;

/**
 * Table: ai_search_default_info
 */
@Data
public class AiSearchDefaultInfo implements Serializable {
    private static final long serialVersionUID = 1L;
    /**
     * Column: id
     * Type: BIGINT
     * Remark: 主键
     */
    private Long id;

    /**
     * Column: content
     * Type: VARCHAR(255)
     * Remark: 搜索内容
     */
    private String content;

    /**
     * Column: content_code
     * Type: VARCHAR(255)
     * Remark: 搜索内容hash编码
     */
    private String contentCode;

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

    /**
     * Column: analysis_type
     * Type: TINYINT(3)
     * Default value: 1
     * Remark: 搜索内容类型识别 1热门解读 2看数值 3看趋势 4看对比 5看排名 6看占比 99收藏
     */
    private Integer analysisType;
}