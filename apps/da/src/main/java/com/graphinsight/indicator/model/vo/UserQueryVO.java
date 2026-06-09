package com.graphinsight.indicator.model.vo;

import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

/**
 * Author: lixiaolong
 * Date: 2022/3/8
 * Desc:
 */
@Data
public class UserQueryVO {

    @ApiModelProperty(value = "域账号或者姓名")
    private String username;

}
