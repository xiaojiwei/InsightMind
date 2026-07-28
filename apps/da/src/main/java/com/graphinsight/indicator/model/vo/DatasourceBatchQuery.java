package com.graphinsight.indicator.model.vo;

import lombok.Data;

import java.util.Set;

/**
 * Date: 2022/9/17
 * Desc:
 */
@Data
public class DatasourceBatchQuery extends BaseVO{
    private Set<Long> ids;
}
