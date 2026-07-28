package com.graphinsight.indicator.model.vo;

import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

import java.util.LinkedList;

/**
 * Date: 2022/2/11
 * Desc:
 */
@Data
public class DimensionFilterCreateVO {

    @ApiModelProperty(value = "维度Code")
    private String dimCode;

    @ApiModelProperty(value = "维度ID")
    private Integer dimId;

    @ApiModelProperty(value = "多个维度的筛选关系 0-and 1-or 不传默认是and")
    private Integer sqlLogicalType = 0;

    @ApiModelProperty(value = "维度英文名")
    private String enName;

    @ApiModelProperty(value = "维度中文名")
    private String cnName;

    @ApiModelProperty(value = "维度对应的筛选条件")
    private LinkedList<DimensionFilterOperatorCreateVO> operatorList;

}
