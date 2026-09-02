package com.graphinsight.indicator.model.vo;

import lombok.Data;

import javax.validation.constraints.Max;
import javax.validation.constraints.Min;

/**
 * @Description: 指标查询参数
 * @Date: 2021/11/16
 */
@Data
public class ModelPageQueryVO extends BaseVO{
    /**
     * cnName
     */
    private String cnName;

    /**
     * enName
     */
    private String enName;

    /**
     * tableName
     */
    private String tableName;


    @Max(value = 100,message = "分页大小最大是100")
    @Min(value = 1,message = "分页大小最小是1")
    private Integer pageSize;

    @Min(value = 1,message = "当前页不能小于1")
    private Integer pageNo;

    private Integer categoryId;

    private String description;

    private String keyword;


    private Integer factTableType;

}
