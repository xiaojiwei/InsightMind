package com.graphinsight.indicator.model.vo;

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
    private Integer dimType = 2;

    @NotBlank
    private String dimTableName;

    @NotBlank
    private String schemaName;

    /**
     * 维度在维表中的列名(group_by的字段名)
     */
    @NotBlank
    private String dimPrimaryKey;

    /**
     * 维度Name名称列列名
     */
    @NotBlank
    private String dimValueColumn;

    /**
     * 维度主键
     */
    @NotNull
    private Integer dimId;

}
