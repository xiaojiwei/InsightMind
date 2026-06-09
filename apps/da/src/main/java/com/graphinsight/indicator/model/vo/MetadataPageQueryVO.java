package com.graphinsight.indicator.model.vo;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

import javax.validation.constraints.Max;
import javax.validation.constraints.Min;

/**
 * @Author: lixiaolong
 * @Description: 指标查询参数
 * @Date: 2021/11/16
 */
@Data
@ApiModel
public class MetadataPageQueryVO extends BaseVO{
    /**
     * shcemaName
     */
    @ApiModelProperty(value = "schema名称",required = false,example = "_statistics_",notes = "数据库库名")
    private String schemaName;

    /**
     * tableName
     */
    @ApiModelProperty(value = "表名",required = false,example = "table_statistic_v1",notes = "表名")
    private String tableName;

    /**
     * tableName
     */
    @ApiModelProperty(value = "字段名",required = false,example = "city_id",notes = "字段名")
    private String columnName;

    @Max(value = 1000,message = "分页大小最大是1000")
    @Min(value = 1,message = "分页大小最小是1")
    @ApiModelProperty(value = "分页大小",required = true,example = "20")
    private Integer pageSize;

    @Min(value = 1,message = "当前页不能小于1")
    @ApiModelProperty(value = "当前页",required = true,example = "1")
    private Integer pageNo;

}
