package com.graphinsight.indicator.model.vo;

import lombok.Data;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;

/**
 * Author: lixiaolong
 * Date: 2023/8/3
 * Desc:
 */
@Data
public class OfflineRequest {

    @NotBlank(message = "下线原因不能为空")
    private String reason;

    @NotNull(message = "ID不能为空")
    private Integer id;

}
