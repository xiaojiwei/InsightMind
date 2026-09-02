package com.graphinsight.indicator.model.vo;

import lombok.Data;

import javax.validation.constraints.Max;
import javax.validation.constraints.Min;


@Data
public class AiSessionVo {
    @Max(value = 100,message = "分页大小最大是100")
    @Min(value = 1,message = "分页大小最小是1")
    private Integer pageSize = 20;
    @Min(value = 1,message = "当前页不能小于1")
    private Integer pageNo = 1;
    private String keyword;

    private String type = "dataGpt";

}
