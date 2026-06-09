package com.graphinsight.indicator.model.vo;

import lombok.Data;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import java.util.List;

/**
 * Author: lixiaolong
 * Date: 2022/2/15
 * Desc:
 */
@Data
public class DimensionValuesCreateVO extends BaseVO {

    @NotBlank(message = "维度Code不能为空")
    private String dimCode;

    @NotNull(message = "维度值列表不能为空")
    private List<DimensionValueItem> dimensionValueItemList;

}
