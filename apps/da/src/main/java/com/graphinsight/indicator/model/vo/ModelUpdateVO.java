package com.graphinsight.indicator.model.vo;

import lombok.Data;

import javax.validation.constraints.NotNull;
import java.util.List;

/**
 * @Description:
 * @Date: 2021/11/22
 */
@Data
public class ModelUpdateVO extends BaseVO{

    @NotNull(message = "主键不能为空")
    private Integer id;

    List<ModelFieldVO> modelFieldList;

}
