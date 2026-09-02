package com.graphinsight.indicator.model.vo;

import lombok.Data;

import java.util.LinkedList;

/**
 * Date: 2022/2/11
 * Desc:
 */
@Data
public class DimensionFilterOperatorCreateVO {

    private LinkedList<String> dataList;

    // private String begin;
    //
    // private String end;

    private Integer sqlLogicalType = 0;

    private Integer sqlOprType;

    private Integer timeRange;

}
