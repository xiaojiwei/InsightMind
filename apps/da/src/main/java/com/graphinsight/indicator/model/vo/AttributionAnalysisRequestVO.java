package com.graphinsight.indicator.model.vo;

import com.graphinsight.indicator.enums.ViewType;

import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

@Data
public class AttributionAnalysisRequestVO {
    @ApiModelProperty(value = "指标编码", required = true)
    private String measureCode;
    @ApiModelProperty(value = "过滤维度编码，一般是日期维度", required = true)
    private String filterDimCode;
    @ApiModelProperty(value = "维度编码")
    private String dimensionCode;
    @ApiModelProperty(value = "基期", required = true)
    private String basePeriod;
    @ApiModelProperty(value = "本期", required = true)
    private String currentPeriod;
    @ApiModelProperty(value = "显示类型", required = true, example = "1 日；2 周；3 月；4 季；5 年")
    private ViewType viewType;
    @ApiModelProperty(value = "趋势：0-持平，1-上升，-1-下降")
    private int trend;
}
