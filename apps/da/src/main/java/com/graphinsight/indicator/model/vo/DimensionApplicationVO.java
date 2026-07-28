package com.graphinsight.indicator.model.vo;

import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

/**
 * Date: 2022/2/14
 * Desc:
 */
@Data
public class DimensionApplicationVO extends BaseVO {

    /**
     * 模型ID
     */
    @ApiModelProperty(value = "模型ID")
    public Integer modelId;


    @ApiModelProperty(value = "维度应用表ID,修改指标表达式时传入")
    private Integer dimAppId;


    @ApiModelProperty(value = "事实表字段英文名")
    private String columnName;

    private Integer available;

    /**
     * 库名
     */
    @ApiModelProperty(value = "库名",required = true,example = "schema_1",notes = "库名")
    private String schemaName;

    /**
     * 表名
     */
    @ApiModelProperty(value = "表名",required = true,example = "table_1",notes = "表名")
    private String tableName;

    private String cnName;

    private String enName;

    private Integer online;


    private String dataType;

    private Integer dimId;

}
