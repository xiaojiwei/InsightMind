package com.graphinsight.indicator.model.vo;

import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

import javax.validation.constraints.Max;
import javax.validation.constraints.Min;
import javax.validation.constraints.NotBlank;


@Data
public class AiMarkerDetailVo {

    @ApiModelProperty(value = "关键字搜索", example = "_statistics_")
    private String keyword;

    @ApiModelProperty(value = "查询类型 home 首页 market 市场", example = "_statistics_")
    private String type = "home";

    @Max(value = 100,message = "分页大小最大是100")
    @Min(value = 1,message = "分页大小最小是1")
    @ApiModelProperty(value = "分页大小",required = true,example = "20")
    private Integer pageSize = 20;

    @Min(value = 1,message = "当前页不能小于1")
    @ApiModelProperty(value = "当前页",required = true,example = "1")
    private Integer pageNo = 1;

}
