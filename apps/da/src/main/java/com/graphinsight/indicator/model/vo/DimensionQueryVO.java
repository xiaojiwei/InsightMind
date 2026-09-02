package com.graphinsight.indicator.model.vo;

import lombok.Data;

import javax.validation.constraints.Max;
import javax.validation.constraints.Min;

/**
 * @Description: 维度查询参数
 * @Date: 2021/11/16
 */
@Data
public class DimensionQueryVO extends BaseVO{
    /**
     * 维度英文名,对应数仓事实表的维度列名
     */
    private String enName;

    /**
     * 维度中文名，全局唯一
     */
    private String cnName;

    private Integer categoryId;

    private String keyword;

    /**
     * 是否是超维
     */
    private Integer isHyper;


    /**
     * 维度的业务描述
     */
    private String description;

    @Max(value = 100,message = "分页大小最大是100")
    @Min(value = 1,message = "分页大小最小是1")
    private Integer pageSize;

    @Min(value = 1,message = "当前页不能小于1")
    private Integer pageNo;


}
