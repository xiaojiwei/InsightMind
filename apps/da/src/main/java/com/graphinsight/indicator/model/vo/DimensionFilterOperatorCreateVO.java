package com.graphinsight.indicator.model.vo;

import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

import java.util.LinkedList;

/**
 * Author: lixiaolong
 * Date: 2022/2/11
 * Desc:
 */
@Data
public class DimensionFilterOperatorCreateVO {

    @ApiModelProperty(value = "维度值列表")
    private LinkedList<String> dataList;

    // @ApiModelProperty(value = "日期维度的开始时间")
    // private String begin;
    //
    // @ApiModelProperty(value = "日期维度的结束时间")
    // private String end;

    @ApiModelProperty(value = "多个条件之间的组合关系 0-and 1-or 不传默认是and")
    private Integer sqlLogicalType = 0;

    @ApiModelProperty(value = "单个条件的筛选类型 比如like、in、eq等,具体取值参考文档")
    private Integer sqlOprType;

    @ApiModelProperty(value = "日期维度的相对值枚举 比如近三天、近一周等,具体取值参考文档")
    private Integer timeRange;

}
