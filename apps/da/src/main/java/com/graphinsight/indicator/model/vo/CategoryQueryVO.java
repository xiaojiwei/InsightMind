package com.graphinsight.indicator.model.vo;

import io.swagger.annotations.ApiModelProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * @Description:
 * @Date: 2021/11/30
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CategoryQueryVO extends BaseVO{

    /**
     * 是否适用于指标
     */
    @ApiModelProperty(value = "指标",example = "true")
    private boolean meas;
    /**
     * 是否适用于模型
     */
    @ApiModelProperty(value = "模型",example = "true")
    private boolean model;

    /**
     * 适用于维度
     */
    @ApiModelProperty(value = "维度",example = "true")
    private boolean dim;


    /**
     * 空间ID
     */
    @ApiModelProperty(value = "空间ID,非必填,传这个值的话返回值中会带有是否有权限的标识，不传的话默认找到全部的分类，且都有权限",example = "1")
    private Long spaceId;

    @ApiModelProperty(value = "是否只查看当前空间 true-是 false-查看其他空间 默认是true")
    private Boolean currentSpace = true;


}
