package com.graphinsight.indicator.model.vo;

import lombok.Data;

import javax.validation.constraints.NotEmpty;
import javax.validation.constraints.NotNull;
import java.util.List;

/**
 * Author: lixiaolong
 * Date: 2022/12/13
 * Desc:
 */
@Data
public class ModelFieldRemove extends BaseVO{

    @NotNull(message = "字段类型不能为空")
    private Integer fieldType;

    @NotEmpty(message = "ID不能为空")
    List<Integer> appIds;
}
