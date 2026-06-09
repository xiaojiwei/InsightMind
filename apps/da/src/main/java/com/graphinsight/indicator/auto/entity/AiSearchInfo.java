package com.graphinsight.indicator.auto.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import lombok.Data;

import java.io.Serializable;
import java.util.Date;

/**
 * Table: ai_search_info
 */
@Data
public class AiSearchInfo  implements Serializable{
    private static final long serialVersionUID = 1L;

    /**
     * 唯一主键
     */
    @TableId(value = "id", type = IdType.AUTO)
    private Integer id;

    /**
     * Column: user_id
     * Type: VARCHAR(255)
     * Remark: 用户唯一标识
     */
    private String userId;

    private String user;

    /**
     * Column: content
     * Type: VARCHAR(255)
     * Remark: 搜索内容
     */
    private Object content;

    /**
     * Column: content_code
     * Type: VARCHAR(255)
     * Remark: 搜索内容hash编码
     */
    private String contentCode;

    /**
     * Column: content_gpt
     * Type: VARCHAR(255)
     * Remark: 搜索openApi解析的内容
     */
    private String contentGpt;

    /**
     * Column: analysis_content
     * Type: VARCHAR(255)
     * Remark: 根据内容解析的查询json结构
     */
    private String analysisContent;

    /**
     * Column: analysis_type
     * Type: TINYINT(3)
     * Remark: 搜索内容类型识别 1看数值 2看趋势 3看对比 4看排名 5看占比
     */
    private Integer analysisType;

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

    /**
     * Column: update_time
     * Type: TIMESTAMP
     * Default value: CURRENT_TIMESTAMP
     * Remark: 更新时间
     */
    private Date updateTime;

    private String measureData;

    private Integer sessionId;

    private String roleType;

    private String questType;
    private String category;
}