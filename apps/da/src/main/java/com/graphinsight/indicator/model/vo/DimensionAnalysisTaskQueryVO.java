package com.graphinsight.indicator.model.vo;

import lombok.Data;

import javax.validation.constraints.Max;
import javax.validation.constraints.Min;
import javax.validation.constraints.NotNull;

/**
 * Date: 2022/7/14
 * Desc:
 */
@Data
public class DimensionAnalysisTaskQueryVO {

    @NotNull
    private Long spaceId;

    private String searchText;

    private boolean mine;

    @Max(value = 100,message = "分页大小最大是100")
    @Min(value = 1,message = "分页大小最小是1")
    private Integer pageSize;

    @Min(value = 1,message = "当前页不能小于1")
    private Integer pageNo;

}
