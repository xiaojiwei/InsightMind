package com.graphinsight.indicator.auto.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import lombok.Data;

import java.io.Serializable;
import java.util.Date;

/**
 * Table: ai_user_collect
 */
@Data
public class AiUserCollect implements Serializable {
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

    /**
     * Column: search_id
     * Type: BIGINT
     * Remark: 搜索内容id
     */
    private Integer searchId;

    /**
     * Column: show_type
     * Type: VARCHAR(255)
     * Remark: 展示类型 number数值 table表格 histogram柱状图 line折线图 pie饼图
     */
    private String showType;

    private String contentCode;


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
}