package com.graphinsight.indicator.model.vo;

import lombok.Data;

/**
 * Date: 2022/2/28
 * Desc:
 */
@Data
public class ModelBaseVO extends BaseVO {

    private Integer id;

    private String cnName;

    private String enName;

    /**
     * 库名
     */
    private String schemaName;

    /**
     * 表名
     */
    private String tableName;

}
