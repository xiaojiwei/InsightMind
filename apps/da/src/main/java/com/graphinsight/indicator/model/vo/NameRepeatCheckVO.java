package com.graphinsight.indicator.model.vo;

import lombok.Data;

import javax.validation.constraints.NotBlank;

/**
 * Date: 2022/3/1
 * Desc:
 */
@Data
public class NameRepeatCheckVO extends BaseVO {

    @NotBlank
    private String name;
}
