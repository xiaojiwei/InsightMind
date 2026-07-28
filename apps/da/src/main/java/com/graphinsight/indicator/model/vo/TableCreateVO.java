package com.graphinsight.indicator.model.vo;

import com.graphinsight.indicator.annotation.LeafCategoryId;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

import javax.validation.constraints.NotBlank;

/**
 * Date: 2022/2/24
 * Desc:
 */
@Data
public class TableCreateVO extends BaseVO {

    @ApiModelProperty(value = "模型主键,更新时传",example = "100")
    private Integer id;

    /**
     * 100-Doris,101-TiDB,102-MySQL
     * 默认Doris
     */
    @ApiModelProperty(value = "数据源",example = "100",notes = "数据源")
    private Integer sourceType = 100;

    /**
     * 模型英文名
     */
    private String enName;

    /**
     * 模型英文名
     */
    @ApiModelProperty(value = "表名")
    private String tableName;

    /**
     * 模型中文名
     */
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
    @NotBlank
    @ApiModelProperty(value = "业务描述",required = true,example = "业务描述")
    private String description;

    /**
     * 叶子分类节点ID
     */
    @LeafCategoryId
    @ApiModelProperty(value = "叶子分类节点ID")
    Integer leafCategoryId;

    /**
     * 开发负责人
     */
    @ApiModelProperty(value = "开发负责人")
    @NotBlank(message = "开发负责人不能为空")
    private String developer;

    /**
     * 库名
     */
    private String schemaName;


    /**
     * 加工方式
     * 0-增量 1-全量
     */
    @ApiModelProperty
    private Integer hasDt;


    /**
     * 加工方式
     * 0-聚合表 1-明细表
     */
    @ApiModelProperty(value = "事实表类型 0-聚合表 1-明细表")
    private Integer factTableType;

}
