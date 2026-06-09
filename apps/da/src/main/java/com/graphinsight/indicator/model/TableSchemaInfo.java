package com.graphinsight.indicator.model;

import lombok.Data;

@Data
public class TableSchemaInfo {

    /**
     * 数据源
     */
    private String source;

    /**
     * db信息
     */
    private String schema;

    /**
     * 表信息
     */
    private String table;

}
