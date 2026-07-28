package com.graphinsight.indicator.model.vo;

import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

import javax.validation.constraints.NotNull;

/**
 * Date: 2022/9/2
 * Desc:
 */
@Data
public class DashboardCopy {

    @ApiModelProperty(value = "文件夹ID")
    private Long folderId;

    @NotNull(message = "版本ID不能为空")
    @ApiModelProperty(value = "版本ID")
    private Long versionId;
}
