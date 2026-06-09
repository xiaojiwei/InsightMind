package com.graphinsight.indicator.model.vo;

import lombok.Data;

/**
 * Author: lixiaolong
 * Date: 2022/7/14
 * Desc: 多维分析各个分项信息
 */
@Data
public class DimensionSubAnalysisVO {

    private Integer dimId;

    private String dimCode;

    private String dimCnName;

    private String gini;

    private Integer viewType;
}
