package com.graphinsight.indicator.model.vo;

import lombok.Data;

import javax.validation.constraints.Max;
import javax.validation.constraints.Min;

/**
 * @Description: 指标查询参数
 * @Date: 2021/11/16
 */
@Data
public class MetadataPageQueryVO extends BaseVO{
    /**
     * shcemaName
     */
    private String schemaName;

    /**
     * tableName
     */
    private String tableName;

    /**
     * tableName
     */
    private String columnName;

    @Max(value = 1000,message = "分页大小最大是1000")
    @Min(value = 1,message = "分页大小最小是1")
    private Integer pageSize;

    @Min(value = 1,message = "当前页不能小于1")
    private Integer pageNo;

}
