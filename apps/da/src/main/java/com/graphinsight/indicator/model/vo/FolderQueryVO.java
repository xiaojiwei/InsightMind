package com.graphinsight.indicator.model.vo;

import lombok.Data;

import javax.validation.constraints.NotNull;

/**
 * Date: 2022/9/2
 * Desc:
 */
@Data
public class FolderQueryVO {

    @NotNull(message = "空间ID不能为空")
    private Long spaceId;

    @NotNull(message = "folderOnly不能为空")
    private Boolean folderOnly;

    private Integer status;

    private Boolean isMine = false;

    private String keyword;
}
