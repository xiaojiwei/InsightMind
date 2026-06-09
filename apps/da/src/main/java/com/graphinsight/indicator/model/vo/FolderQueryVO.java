package com.graphinsight.indicator.model.vo;

import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

import javax.validation.constraints.NotNull;

/**
 * Author: lixiaolong
 * Date: 2022/9/2
 * Desc:
 */
@Data
public class FolderQueryVO {

    @NotNull(message = "空间ID不能为空")
    @ApiModelProperty(value = "空间ID")
    private Long spaceId;

    @NotNull(message = "folderOnly不能为空")
    @ApiModelProperty(value = "true-只查文件夹,false-带看板")
    private Boolean folderOnly;

    private Integer status;

    private Boolean isMine = false;

    private String keyword;
}
