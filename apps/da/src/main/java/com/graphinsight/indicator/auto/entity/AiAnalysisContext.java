package com.graphinsight.indicator.auto.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import lombok.Data;

import java.io.Serializable;
import java.util.Date;

/**
 * Table: ai_analysis_context
 */
@Data
public class AiAnalysisContext  implements Serializable {
    private static final long serialVersionUID = 1L;
    /**
     * Column: id
     * Type: BIGINT
     * Remark: 主键
     */
    @TableId(value = "id", type = IdType.AUTO)
    private Integer id;

    /**
     * Column: content_code
     * Type: VARCHAR(255)
     * Remark: 搜索内容hash编码
     */
    private String contentCode;

    /**
     * Column: analysis_content
     * Type: VARCHAR(255)
     * Remark: 根据内容解析的查询json结构
     */
    private String analysisContent;

    private String baseInfo;

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