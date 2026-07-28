package com.graphinsight.indicator.model.vo;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

import javax.validation.constraints.Max;
import javax.validation.constraints.Min;

/**
 * @Description: 维度查询参数
 * @Date: 2021/11/16
 */
@Data
@ApiModel
public class DimensionQueryVO extends BaseVO{
    /**
     * 维度英文名,对应数仓事实表的维度列名
     */
    @ApiModelProperty(value = "维度英文名",required = false,example = "order_num",notes = "维度字段名")
    private String enName;

    /**
     * 维度中文名，全局唯一
     */
    @ApiModelProperty(value = "维度中文名",required = false,example = "订单总量",notes = "维度字段中文名")
    private String cnName;

    @ApiModelProperty(value = "分类ID",example = "1")
    private Integer categoryId;

    @ApiModelProperty(value = "关键字搜索文本",example = "订单量、order_num")
    private String keyword;

    /**
     * 是否是超维
     */
    private Integer isHyper;


    /**
     * 维度的业务描述
     */
    @ApiModelProperty(value = "维度业务描述",required = false,example = "所有订单数量总和")
    private String description;

    @Max(value = 100,message = "分页大小最大是100")
    @Min(value = 1,message = "分页大小最小是1")
    @ApiModelProperty(value = "分页大小",required = true,example = "20")
    private Integer pageSize;

    @Min(value = 1,message = "当前页不能小于1")
    @ApiModelProperty(value = "当前页",required = true,example = "1")
    private Integer pageNo;


}
