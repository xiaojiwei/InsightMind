package com.graphinsight.indicator.model.vo;

import com.graphinsight.indicator.annotation.LeafCategoryId;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;

/**
 * Author: lixiaolong
 * Date: 2022/2/24
 * Desc:
 */
@Data
public class ModelPreCreateVO extends BaseVO{

    @ApiModelProperty(value = "ID")
    private Integer id;

    /**
     * 100-Doris,101-TiDB,102-MySQL
     * 默认Doris
     */
    @ApiModelProperty(value = "数据源",example = "100",notes = "数据源")
    private Integer sourceType = 100;

    /**
     * 库名
     */
    @NotBlank
    @ApiModelProperty(value = "库名",required = true,example = "schema_1",notes = "库名")
    private String schemaName;

    /**
     * 表名
     */
    @NotBlank(message = "表名不能为空")
    @ApiModelProperty(value = "表名",required = true,example = "table_1",notes = "表名")
    private String tableName;

    /**
     * 模型英文名
     */
    @NotBlank(message = "模型英文名不能为空")
    private String enName;

    /**
     * 模型中文名
     */
    @NotBlank(message = "模型中文名不能为空")
    private String cnName;

//    /**
//     * 是否在线 1-下线 0-下线
//     */
//    private Integer online;

    /**
     * 备注
     */
    private String remark;

    /**
     * 业务描述
     */
    @NotBlank(message = "业务描述不能为空")
    @ApiModelProperty(value = "业务描述",required = true,example = "业务描述")
    private String description;

    @NotNull(message = "分类ID不能为空")
    @LeafCategoryId
    @ApiModelProperty(value = "分类节点ID",required = true)
    private Integer leafCategoryId;
}
