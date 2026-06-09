package com.graphinsight.indicator.model.vo;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.graphinsight.indicator.auto.entity.User;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

import javax.validation.constraints.NotBlank;
import java.util.List;

/**
 * @Author: lixiaolong
 * @Description:
 * @Date: 2021/11/16
 */
@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
@ApiModel(value = "维度视图", description = "创建维度的参数")
public class DimensionVO extends BaseVO{

    @ApiModelProperty(value = "维度code")
    private String code;

    private Integer id;

    /**
     * 维度英文名,对应数仓事实表的维度列名
     */
    @NotBlank(message = "维度英文名不能为空")
    @ApiModelProperty(value = "维度英文名",required = true,example = "city_id",notes = "维度字段名")
    private String enName;


    /**
     * 维度中文名，全局唯一
     */
    @NotBlank(message = "维度中文名不能为空")
    @ApiModelProperty(value = "维度中文名",required = true,example = "订单总量",notes = "维度字段中文名")
    private String cnName;

   /**
    * 是否在线 1-下线 0-下线
    */
   private Integer online = 1;

    /**
     * 是否可拖拽查询 1-可以 0-不可以
     */
    private Integer draggable;

    /**
     * 维度的业务描述
     */
    @NotBlank(message = "维度业务描述不能为空")
    @ApiModelProperty(value = "维度业务描述",required = true,example = "所有订单数量总和")
    private String description;

    /**
     * 分类信息
     */
    List<CategoryVO> categoryInfo;

    @ApiModelProperty(value = "维度相关模型")
    private List<DimensionApplicationVO> relatedModel;

    @ApiModelProperty(value = "维度相关指标")
    private List<MeasureVO> relatedMeasure;

    /**
     * 叶子分类节点ID
     */
    @ApiModelProperty(value = "叶子分类节点ID")
    Integer leafCategoryId;

//    @NotNull(message = "viewType不能为空")
    @ApiModelProperty(value = "显示类型 0 字符；1 日；2 周；3 月；4 季；5 年；6 小时",required = true)
    private Integer viewType = 0;

    @ApiModelProperty(value = "创建人",example = "张三")
    private User creator;

    @ApiModelProperty(value = "负责人",example = "张三")
    private User developer;

    @ApiModelProperty(value = "创建时间")
    private Long createTime;

    @ApiModelProperty(value = "更新人")
    private User updater;

    @ApiModelProperty(value = "更新时间")
    private Long updateTime;

    @ApiModelProperty(value = "维表",example = "nr_sales_dim_dept_info",required = true)
    private String dimTableName;

    @ApiModelProperty(value = "schemaName",example = "eps_test",required = true)
    private String schemaName;

    /**
     * 维度在维表中的列名(group_by的字段名)
     */
    @ApiModelProperty(value = "查询字段",example = "city_id",required = true)
    private String queryField;

    /**
     * 维度Name名称列列名
     */
    @ApiModelProperty(value = "显示字段",example = "city_name",required = true)
    private String displayField;

    private String whereCondition;

    /**
     * 维度Name名称列列名
     */
    @ApiModelProperty(value = "备注")
    private String remark;

    @ApiModelProperty(value = "前端维度类型 0-无维表 1-有维表")
    private Integer frontDimType;

    @ApiModelProperty(value = "维度类型 0-退化维 1-有维码无维表 2-标准维")
    private Integer dimType;

    /**
     * 是否是超维
     */
    private Integer isHyper;



    @ApiModelProperty(value = "级别信息")
    private List<LevelVO> levels;
}
