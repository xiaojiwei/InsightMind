package com.graphinsight.indicator.model.dto;

import lombok.Data;

/**
 * Date: 2022/2/24
 * Desc:
 */
@Data
public class DwColumnDTO {

    /**
     * 主键
     */
    private Integer id;

    /**
     * 表ID
     */
    private Integer dwTableId;

    /**
     * 数据类型
     */
    private String dataType;

    /**
     * 分类
     */
    private Integer leafCategoryId;

    /**
     * 字段描述
     */
    private String description;

    private String columnEnName;

    private String columnCnName;

    private Integer viewType;



    /**
     * 字段类型1-指标 2-维度
     */
    private Integer type;

}
