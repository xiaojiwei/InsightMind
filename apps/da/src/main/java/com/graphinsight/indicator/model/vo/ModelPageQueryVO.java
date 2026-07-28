package com.graphinsight.indicator.model.vo;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

import javax.validation.constraints.Max;
import javax.validation.constraints.Min;

/**
 * @Description: 指标查询参数
 * @Date: 2021/11/16
 */
@Data
@ApiModel
public class ModelPageQueryVO extends BaseVO{
    /**
     * cnName
     */
    @ApiModelProperty(value = "模型中文名",example = "_statistics_")
    private String cnName;

    /**
     * enName
     */
    @ApiModelProperty(value = "模型英文名",example = "table_statistic_v1")
    private String enName;

    /**
     * tableName
     */
    @ApiModelProperty(value = "事实表名称",example = "table_statistic_v1")
    private String tableName;


    @Max(value = 100,message = "分页大小最大是100")
    @Min(value = 1,message = "分页大小最小是1")
    @ApiModelProperty(value = "分页大小",required = true,example = "20")
    private Integer pageSize;

    @Min(value = 1,message = "当前页不能小于1")
    @ApiModelProperty(value = "当前页",required = true,example = "1")
    private Integer pageNo;

    @ApiModelProperty(value = "分类ID",required = true,example = "1")
    private Integer categoryId;

    private String description;

    @ApiModelProperty(value = "关键字 中英文名",example = "_statistics_")
    private String keyword;


    @ApiModelProperty(value = "事实表类型 0-聚合表 1-明细表")
    private Integer factTableType;

}
