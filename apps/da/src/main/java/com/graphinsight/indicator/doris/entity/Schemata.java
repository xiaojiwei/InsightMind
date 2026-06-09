package com.graphinsight.indicator.doris.entity;

import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serializable;

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
public class Schemata implements Serializable {

    private static final long serialVersionUID = 1L;

    private String schemaName;

}
