package com.graphinsight.indicator.model.vo;

import lombok.Data;

import javax.validation.constraints.NotNull;
import java.util.List;

/**
 * Date: 2022/4/14
 * Desc:
 */
@Data
public class MeasureExpUpdateVO extends MeasureExpBaseVO {

    @NotNull(message = "指标应用ID不能为空")
    private Integer measAppId;

    private List<NaturalDimConfigVO> naturalDimConfig;

}
