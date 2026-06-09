package com.graphinsight.indicator.model.vo;

import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

import java.util.List;

/**
 * Author: lixiaolong
 * Date: 2022/3/29
 * Desc:
 */
@Data
public class CategorySeqUpdateVO {

    @ApiModelProperty(value = "同一层级的中文名顺序列表,ids和cnNames都传，以ids为主")
    private List<String> cnNames;

    @ApiModelProperty(value = "同一层级的id顺序列表,ids和cnNames都传，以ids为主")
    private List<Integer> ids;
}
