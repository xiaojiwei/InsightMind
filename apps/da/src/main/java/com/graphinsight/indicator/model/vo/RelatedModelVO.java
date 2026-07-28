package com.graphinsight.indicator.model.vo;

import lombok.Data;

/**
 * Date: 2022/3/15
 * Desc:
 */
@Data
public class RelatedModelVO {
    private static final long serialVersionUID = 1L;

    private Integer applyType;

    /**
     * 主键
     */
    private Integer id;


    /**
     * 100-Doris,101-TiDB,102-MySQL
     */
    private Integer sourceType;

    /**
     * 库名
     */
    private String schemaName;

    /**
     * 表名
     */
    private String tableName;

    /**
     * 0-事实表；1-维度表
     */
    private Integer type;

    /**
     * 是否在线 1-下线 0-下线
     */
    private Integer online;

    /**
     * 模型英文名
     */
    private String enName;

    /**
     * 模型中文名
     */
    private String cnName;


    /**
     * 备注
     */
    private String remark;

    /**
     * 表描述
     */
    private String description;

    /**
     * 叶子节点分类ID
     */
    private Integer leafCategoryId;


    private Integer hasDt;


    /**
     * 是否是聚合表
     * 0-否 1-是
     */
    private Integer aggregationTable;
}
