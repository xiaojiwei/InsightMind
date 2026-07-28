package com.graphinsight.indicator.model.vo;

import com.graphinsight.indicator.auto.entity.User;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

import javax.validation.constraints.NotBlank;
import java.util.List;

/**
 * @Description:
 * @Date: 2021/11/22
 */
@Data
public class ModelDetailVO extends BaseVO{

    @ApiModelProperty(value = "模型主键",example = "100")
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

    @ApiModelProperty(value = "开发负责人",example = "张三")
    private User developer;

    @ApiModelProperty(value = "创建时间")
    private Long createTime;

    @ApiModelProperty(value = "更新人")
    private User updater;

    @ApiModelProperty(value = "更新时间")
    private Long updateTime;

    @ApiModelProperty(value = "属性列表")
    List<ModelFieldVO> modelFieldList;

    /**
     * 叶子分类节点ID
     */
    @ApiModelProperty(value = "叶子分类节点ID")
    Integer leafCategoryId;

    /**
     * 是否需要卡dt
     * 0-否 1-是
     */
    private Integer hasDt;




    /**
     * 加工方式
     * 0-聚合表 1-明细表
     */
    @ApiModelProperty(value = "事实表类型 0-聚合表 1-明细表")
    private Integer factTableType;





}
