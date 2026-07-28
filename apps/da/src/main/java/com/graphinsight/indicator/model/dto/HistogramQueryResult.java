package com.graphinsight.indicator.model.dto;

import lombok.Data;

/**
 * Date: 2023/1/4
 * Desc:
 */
@Data
public class HistogramQueryResult {

    private Long rowNum;

    private String tableName;

    private String dimCode;

    private String sql;

}
