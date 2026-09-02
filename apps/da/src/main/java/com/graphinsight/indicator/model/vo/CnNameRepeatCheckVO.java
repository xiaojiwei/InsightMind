package com.graphinsight.indicator.model.vo;

import lombok.Data;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;

/**
 * Date: 2022/3/1
 * Desc:
 */
@Data
public class CnNameRepeatCheckVO extends BaseVO {

    @NotNull
    private Integer type;

    @NotBlank
    private String cnName;
}
