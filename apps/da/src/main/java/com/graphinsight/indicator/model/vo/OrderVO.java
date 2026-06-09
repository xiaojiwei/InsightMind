package com.graphinsight.indicator.model.vo;

import com.graphinsight.indicator.enums.SortBy;
import com.graphinsight.indicator.enums.SortType;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

/**
 * Author: lixiaolong
 * Date: 2022/8/3
 * Desc:
 */
@Data
public class OrderVO {

    @ApiModelProperty(value = "排序字段 参考SortBy枚举类")
    private SortBy sortBy;

    @ApiModelProperty(value = "排序code,sortBy是指标或维度时，必填")
    private String code;

    private Integer sortType = SortType.DESC.getCode();

}
