package com.graphinsight.indicator.model.dto;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@ApiModel("资源详情")
public class RelatedResourceDTO {

    @ApiModelProperty("资源序号")
    private Integer id;

    @ApiModelProperty("资源类型0数据集1数据看板2拆解树3多维分析4指标预警5目标管理")
    private Integer type;

    @ApiModelProperty("资源类型名称")
    private String typeName;

    @ApiModelProperty("资源名称")
    private String name;

    @ApiModelProperty("资源id")
    private Long resourceId;

    @ApiModelProperty("空间id")
    private Long spaceId;

    @ApiModelProperty("空间名称")
    private String spaceName;

    @ApiModelProperty("资源创建人")
    private String creator;

    @ApiModelProperty("资源创建时间")
    private LocalDateTime createDate;
}
