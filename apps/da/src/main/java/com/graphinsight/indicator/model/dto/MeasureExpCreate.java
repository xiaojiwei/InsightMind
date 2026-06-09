package com.graphinsight.indicator.model.dto;

import com.graphinsight.indicator.enums.SqlAggFunType;
import lombok.Data;

/**
 * Author: lixiaolong
 * Date: 2022/4/14
 * Desc:
 */
@Data
public class MeasureExpCreate {

    private Integer measureId;

    private String columnEnName;

    private Integer modelId;

    private Integer measAppId;

    private String whereCondition;

    private SqlAggFunType sqlAggFunType;

}
