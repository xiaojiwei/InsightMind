package com.graphinsight.indicator.model.vo;

import lombok.Data;

import javax.validation.constraints.NotNull;

/**
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
    public Integer leafCategoryId = -2;
}
