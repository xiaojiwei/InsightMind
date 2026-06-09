package com.graphinsight.indicator.auto.entity;

import lombok.Data;

import java.util.Date;

/**
 * Table: ai_user_view_count
 */
@Data
public class AiUserViewCount {
    /**
     * Column: id
     * Type: BIGINT
     * Remark: 主键
     */
    private Long id;

    /**
     * Column: user_id
     * Type: VARCHAR(255)
     * Remark: 用户唯一标识
     */
    private String userId;

    /**
     * Column: search_id
     * Type: BIGINT
     * Remark: 搜索内容id
     */
    private Long searchId;

    /**
     * Column: count_total
     * Type: BIGINT
     * Default value: 0
     * Remark: 浏览次数
     */
    private Long countTotal;

    /**
     * Column: count_type
     * Type: TINYINT(3)
     * Remark: 搜索内容类型识别 1看数值 2看趋势 3看对比 4看排名 5看占比 100所有类型
     */
    private Integer countType;

    /**
     * Column: is_del
     * Type: TINYINT(3)
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
}