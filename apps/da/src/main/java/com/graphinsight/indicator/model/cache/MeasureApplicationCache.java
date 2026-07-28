package com.graphinsight.indicator.model.cache;

import lombok.Data;

/**
 * Date: 2022/3/15
 * Desc:
 */
@Data
public class MeasureApplicationCache {

    private Integer measAppId;

    private Integer measId;

    private Integer applyType;

    private Integer relatedDwTableId;

    private String dataFormatStr;

    private Integer decimalPlaces;
    private Integer dataScale;

}
