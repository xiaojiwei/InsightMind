package com.graphinsight.indicator.model.vo;

import io.swagger.annotations.ApiModelProperty;
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
    @ApiModelProperty(value = "模型主键",example = "100")
    private Integer id;

    @ApiModelProperty(value = "属性列表")
    List<ModelFieldVO> modelFieldList;

}
