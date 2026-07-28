package com.graphinsight.indicator.model.vo;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

import javax.validation.constraints.Max;
import javax.validation.constraints.Min;
import java.util.List;

/**
 * @Description: 指标查询参数
 * @Date: 2021/11/16
 */
@Data
@ApiModel
public class MeasureQueryVO extends BaseVO{
    /**
     * 指标英文名,对应数仓事实表的指标列名
     */
    @ApiModelProperty(value = "指标英文名",required = false,example = "order_num",notes = "指标字段名")
    private String enName;

    /**
     * 指标中文名，全局唯一
     */
    @ApiModelProperty(value = "指标中文名",required = false,example = "订单总量",notes = "指标字段中文名")
    private String cnName;

    @ApiModelProperty(value = "分类ID",example = "1")
    private Integer categoryId;


    @ApiModelProperty(value = "关键字搜索文本",example = "订单量、order_num")
    private String keyword;

    /**
     * 指标的业务描述
     */
    @ApiModelProperty(value = "指标业务描述",required = false,example = "所有订单数量总和")
    private String description;

    @Max(value = 100,message = "分页大小最大是100")
    @Min(value = 1,message = "分页大小最小是1")
    @ApiModelProperty(value = "分页大小",required = true,example = "20")
    private Integer pageSize = 20;

    @Min(value = 1,message = "当前页不能小于1")
    @ApiModelProperty(value = "当前页",required = true,example = "1")
    private Integer pageNo = 1;

    @ApiModelProperty(value = "指标范围,0-公司 1-一级部门 2-二级部门 3-三级部门",example = "1")
    private List<Integer> deptLevels;

}
