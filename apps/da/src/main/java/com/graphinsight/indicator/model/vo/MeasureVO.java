package com.graphinsight.indicator.model.vo;

import com.graphinsight.indicator.auto.entity.Department;
import com.graphinsight.indicator.auto.entity.User;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

import javax.validation.constraints.NotBlank;
import java.util.ArrayList;
import java.util.List;

/**
 * @Description:
 * @Date: 2021/11/16
 */
@Data
@ApiModel(value = "MeasureVO", description = "创建指标的参数")
public class MeasureVO extends BaseVO{

    private Integer id;


    /**
     * 指标英文名,对应数仓事实表的指标列名
     */
    @NotBlank(message = "指标英文名不能为空")
    @ApiModelProperty(value = "指标英文名",required = true,example = "order_num",notes = "指标字段名")
    private String enName;

    @ApiModelProperty(value = "指标code")
    private String code;

    /**
     * 指标中文名，全局唯一
     */
    @NotBlank(message = "指标中文名不能为空")
    @ApiModelProperty(value = "指标中文名",required = true,example = "订单总量",notes = "指标字段中文名")
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
     * 指标单位
     */
    @ApiModelProperty(value = "指标中文名",required = false,example = "万元",notes = "指标展示的单位")
    private String unit;

    /**
     * 指标口径
     */
    @ApiModelProperty(value = "指标口径",required = false,example = "所有订单数量总和",notes = "指标的统计口径")
    private String caliber;

    /**
     * 指标的业务描述
     */
    @NotBlank(message = "指标业务描述不能为空")
    @ApiModelProperty(value = "指标业务描述",required = true,example = "所有订单数量总和")
    private String description;

    /**
     * 分类信息
     */
    List<CategoryVO> categoryInfo;

    /**
     * 叶子分类节点ID
     */
    @ApiModelProperty(value = "叶子分类节点ID")
    Integer leafCategoryId;

    @ApiModelProperty(value = "指标相关模型")
    private List<ModelVO> relatedModel;

    @ApiModelProperty(value = "指标相关维度")
    private List<DimensionVO> relatedDimension;

    @ApiModelProperty(value = "创建人",example = "张三")
    private User creator;

    @ApiModelProperty(value = "创建时间")
    private Long createTime;

    @ApiModelProperty(value = "更新人")
    private User updater;

    @ApiModelProperty(value = "更新时间")
    private Long updateTime;

    /**
     * 维度Name名称列列名
     */
    @ApiModelProperty(value = "备注")
    private String remark;

    /**
     * 指标开发者
     */
    private User developer;

    /**
     * 指标负责人
     */
    private User owner;

    /**
     * 部门信息
     */
    private Department department;

    /**
     * 指标范围
     */
    @ApiModelProperty(value = "指标范围 0-公司级 1-一级 2-二 ...")
    private Integer deptLevel;


    /**
     * 是否是北极星指标
     * 0-否 1-是
     */
    @ApiModelProperty(value = "是否是北极星指标 0-否 1-是")
    private Integer northStar;

    /**
     * 指标表达式
     */
    List<ComplexMeasureBaseVO> measureExpressions = new ArrayList<>();

    private List<String> aliases = new ArrayList<>();
}
