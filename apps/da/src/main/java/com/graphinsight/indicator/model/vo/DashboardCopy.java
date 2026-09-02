package com.graphinsight.indicator.model.vo;

import lombok.Data;

import javax.validation.constraints.NotNull;

/**
 * Date: 2022/9/2
 * Desc:
 */
@Data
public class DashboardCopy {

    private Long folderId;

    @NotNull(message = "版本ID不能为空")
    private Long versionId;
}
