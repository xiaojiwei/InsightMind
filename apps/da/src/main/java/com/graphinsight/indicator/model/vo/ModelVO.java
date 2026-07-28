package com.graphinsight.indicator.model.vo;

import com.graphinsight.indicator.auto.entity.User;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

import javax.validation.constraints.NotBlank;
import java.util.List;

/**
 * @Description:
 * @Date: 2021/11/16
 */
@Data
@ApiModel(value = "ModelVO", description = "创建模型参数")
public class ModelVO extends BaseVO{


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
    @NotBlank
    @ApiModelProperty(value = "表名",required = true,example = "table_1",notes = "表名")
    private String tableName;

    /**
     * 模型英文名
     */
    private String enName;

    /**
     * 模型中文名
     */
    private String cnName;

   /**
    * 是否在线 1-下线 0-下线
    */
   private Integer online;

    /**
     * 分类信息
     */
    List<CategoryVO> categoryInfo;

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

    @ApiModelProperty(value = "创建人",example = "张三")
    private User creator;

    @ApiModelProperty(value = "创建时间")
    private Long createTime;

    @ApiModelProperty(value = "更新人")
    private User updater;

    @ApiModelProperty(value = "更新时间")
    private Long updateTime;

    /**
     * 叶子分类节点ID
     */
    @ApiModelProperty(value = "叶子分类节点ID")
    Integer leafCategoryId;

    /**
     * 指标表达式
     */
    List<ComplexMeasureBaseVO> measureExpressions;


    /**
     * 事实表类型
     * 0-聚合表
     * 1-明细表
     */
    private Integer factTableType;


}
