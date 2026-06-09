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
public class Tables implements Serializable {

    private static final long serialVersionUID = 1L;

    private String tableSchema;
    private String tableName;
    private String tableType;
    private Long version;
    private String tableComment;


}
