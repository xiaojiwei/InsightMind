package com.graphinsight.indicator.model.vo;

import lombok.Data;

import java.util.List;

/**
 * Date: 2022/4/27
 * Desc:
 */
@Data
public class ComplexMeasureRelyInfo {
    private Integer measAppId;
    private List<SimpleInfo> relatedDwTables;
    private List<SimpleInfo> relyBaseDimensions;
    private List<SimpleInfo> relyDimensions;
    private List<SimpleInfo> relyMeasures;
    private List<SimpleInfo> relyBaseMeasures;
}
