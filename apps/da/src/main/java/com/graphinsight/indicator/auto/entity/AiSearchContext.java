package com.graphinsight.indicator.auto.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import lombok.Data;

import java.io.Serializable;
import java.util.Date;

/**
 * Table: ai_search_context
 */
@Data
public class AiSearchContext implements Serializable{

    private static final long serialVersionUID = 1L;
    /**
     * Column: id
     * Type: BIGINT
     * Remark: 主键
     */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /**
     * Column: search_id
     * Type: BIGINT
     * Remark: 搜索内容id
     */
    private Integer searchId;

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
     * Column: content_context
     * Type: TEXT
     * Remark: 搜索内容
     */
    private String contentContext;

}