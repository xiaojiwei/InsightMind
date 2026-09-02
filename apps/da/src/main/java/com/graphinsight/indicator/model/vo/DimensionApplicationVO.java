package com.graphinsight.indicator.model.vo;

import lombok.Data;

/**
 * Date: 2022/2/14
 * Desc:
 */
@Data
public class DimensionApplicationVO extends BaseVO {

    /**
     * 模型ID
     */
    public Integer modelId;


    private Integer dimAppId;


    private String columnName;

    private Integer available;

    /**
     * 库名
     */
    private String schemaName;

    /**
     * 表名
     */
    private String tableName;

    private String cnName;

    private String enName;

    private Integer online;


    private String dataType;

    private Integer dimId;

}
