package com.graphinsight.indicator.model.vo;

import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;

/**
 * @Description:
 * @Date: 2021/11/17
 */
@Data
public class DimTableConfigVO extends BaseVO{

    /**
     * 0-退化维；1-标准维无维表；2-标准为有维表
     */
    @ApiModelProperty(value = "维度类型",example = "2",notes = "0-退化维；1-标准维无维表；2-标准为有维表")
    private Integer dimType = 2;

    @NotBlank
    @ApiModelProperty(value = "维表",example = "nr_sales_dim_dept_info",required = true)
    private String dimTableName;

    @NotBlank
    @ApiModelProperty(value = "schemaName",example = "eps_test",required = true)
    private String schemaName;

    /**
     * 维度在维表中的列名(group_by的字段名)
     */
    @NotBlank
    @ApiModelProperty(value = "编码字段",example = "city_id",required = true)
    private String dimPrimaryKey;

    /**
     * 维度Name名称列列名
     */
    @NotBlank
    @ApiModelProperty(value = "码值字段",example = "city_name",required = true)
    private String dimValueColumn;

    /**
     * 维度主键
     */
    @NotNull
    @ApiModelProperty(value = "维度ID",example = "1",required = true)
    private Integer dimId;

}
