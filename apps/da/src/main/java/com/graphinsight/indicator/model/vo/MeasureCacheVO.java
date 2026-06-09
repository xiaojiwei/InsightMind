package com.graphinsight.indicator.model.vo;

import lombok.Data;

import java.util.List;

/**
 * Author: lixiaolong
 * Date: 2022/4/27
 * Desc:
 */
@Data
public class MeasureCacheVO {
    private String cnName;
    private String enName;
    private Integer id;
    private List<SimpleInfo> relatedDimensions;
    private List<SimpleInfo> relatedModels;
    private List<ComplexMeasureRelyInfo> relyInfos;
}
