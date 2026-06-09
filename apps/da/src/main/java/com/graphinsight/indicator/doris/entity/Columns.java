package com.graphinsight.indicator.doris.entity;

import java.io.Serializable;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * <p>
 * SCHEMA
 * </p>
 *
 * @author lixiaolong
 * @since 2021-11-17
 */
@Data
@EqualsAndHashCode(callSuper = false)
public class Columns implements Serializable {

    private static final long serialVersionUID = 1L;
    private String tableName;
    private String columnName;
    private String dataType;
    private String columnType;
    private String tableSchema;
    /**
     * 字段中文描述
     * 默认作为中文名
     */
    private String columnComment;

}
