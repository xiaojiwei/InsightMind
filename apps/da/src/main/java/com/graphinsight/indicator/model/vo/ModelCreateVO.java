package com.graphinsight.indicator.model.vo;

import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;
import java.util.List;

/**
 * @Author: lixiaolong
 * @Description:
 * @Date: 2021/12/8
 */
@Data
public class ModelCreateVO extends BaseVO {

    @NotNull(message = "模型ID不能为空")
    @ApiModelProperty(value = "模型ID")
    private Integer id;

//     /**
//      * 100-Doris,101-TiDB,102-MySQL
//      * 默认Doris
//      */
//     @ApiModelProperty(value = "数据源",example = "100",notes = "数据源")
//     private Integer sourceType = 100;
//
//     /**
//      * 库名
//      */
//     @NotBlank
//     @ApiModelProperty(value = "库名",required = true,example = "schema_1",notes = "库名")
//     private String schemaName;
//
//     /**
//      * 表名
//      */
//     @NotBlank(message = "表名不能为空")
//     @ApiModelProperty(value = "表名",required = true,example = "table_1",notes = "表名")
//     private String tableName;
//
//     /**
//      * 模型英文名
//      */
//     @NotBlank(message = "模型英文名不能为空")
//     private String enName;
//
//     /**
//      * 模型中文名
//      */
//     @NotBlank(message = "模型中文名不能为空")
//     private String cnName;
//
// //    /**
// //     * 是否在线 1-下线 0-下线
// //     */
// //    private Integer online;
//
//     /**
//      * 备注
//      */
//     private String remark;
//
//     /**
//      * 业务描述
//      */
//     @NotBlank(message = "业务描述不能为空")
//     @ApiModelProperty(value = "业务描述",required = true,example = "业务描述")
//     private String description;
//
//     @NotNull(message = "分类ID不能为空")
//     @LeafCategoryId
//     @ApiModelProperty(value = "分类节点ID",required = true)
//     private Integer leafCategoryId;

    @Size(min = 1,message = "请至少选择一个字段同步")
    @NotNull(message = "同步的字段列表不能为空")
    @ApiModelProperty(value = "同步的字段列表",required = true)
    List<OriginMeasureCreateFieldVO> columns;
}
