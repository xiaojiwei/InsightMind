package com.graphinsight.indicator.model.vo;

import lombok.Data;

import java.util.LinkedList;

/**
 * Date: 2022/2/11
 * Desc:
 */
@Data
public class DimensionFilterCreateVO {

    private String dimCode;

    private Integer dimId;

    private Integer sqlLogicalType = 0;

    private String enName;

    private String cnName;

    private LinkedList<DimensionFilterOperatorCreateVO> operatorList;

}
