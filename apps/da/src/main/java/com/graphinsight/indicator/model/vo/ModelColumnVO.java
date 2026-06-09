package com.graphinsight.indicator.model.vo;

import com.graphinsight.indicator.auto.entity.User;
import com.graphinsight.indicator.enums.EntryType;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

/**
 * Author: lixiaolong
 * Date: 2023/2/3
 * Desc:
 */
@Data
public class ModelColumnVO {

    @ApiModelProperty(value = "字段名")
    private String columnName;

    @ApiModelProperty(value = "字段描述")
    private String columnComment;

    @ApiModelProperty(value = "英文名")
    private String enName;

    @ApiModelProperty(value = "中文名")
    private String cnName;

    @ApiModelProperty(value = "维度ID")
    private Integer dimensionId;

    @ApiModelProperty(value = "字段类型")
    private String dataType;

    @ApiModelProperty(value = "录入类型 0-新建 1-关联")
    private EntryType entryType;

    @ApiModelProperty(value = "分类信息")
    private CategoryVO category;

    @ApiModelProperty(value = "描述信息")
    private String description;

    @ApiModelProperty(value = "开发负责人")
    private User developer;

    private Integer viewType;
}
