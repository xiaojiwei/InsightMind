package com.graphinsight.indicator.model.dto;

import lombok.Data;

import java.math.BigDecimal;

/**
 * Author: lixiaolong
 * Date: 2023/1/4
 * Desc:
 */
@Data
public class HistogramInfo {

    private String dimCode;

    private Long dimensionRowNum;

    private String tableName;

    private Long tableRowNum;

    private Long maxScanNum;

    /**
     * 维度离散程度
     * dispersionDegree = dimensionRowNum / tableRowNum
     * 值介于(0,1] 值越大说明维度的粒度越小，查询性能越差
     */
    private BigDecimal dispersionDegree;
}
