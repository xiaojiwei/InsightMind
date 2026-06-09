package com.graphinsight.indicator.model.vo;

import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

import javax.validation.constraints.NotNull;

/**
 * @Author: lixiaolong
 * @Description: 指标、血缘基本信息
 * @Date: 2021/11/23
 */
@Data
public class BaseInfo {
    public Integer id;
    public String enName;
    public String cnName;
    public String code;
    private Integer online;
    public Integer viewType;
    public String description;

    /**
     * 叶子分类ID
     */
    @NotNull
    @ApiModelProperty(value = "指标二级分类ID",required = false,example = "1")
    public Integer leafCategoryId = -2;
}
