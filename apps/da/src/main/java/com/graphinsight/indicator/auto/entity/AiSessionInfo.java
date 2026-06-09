package com.graphinsight.indicator.auto.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.Date;

/**
 * Table: ai_search_info
 */
@Data
@EqualsAndHashCode(callSuper = false)
public class AiSessionInfo extends BaseEntityV4 implements Serializable{
    private static final long serialVersionUID = 1L;

    /**
     * 唯一主键
     */
    @TableId(value = "id", type = IdType.AUTO)
    private Integer id;


    /**
     * Column: content_gpt
     * Type: VARCHAR(255)
     * Remark: 搜索openApi解析的内容
     */
    private String type;

    /**
     * Column: analysis_content
     * Type: VARCHAR(255)
     * Remark: 根据内容解析的查询json结构
     */
    private String name;


    /**
     * Column: is_del
     * Type: TINYINT(3)
     * Remark: 是否删除 0否 1是
     */
    private Integer isDel;

}