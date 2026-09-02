package com.graphinsight.indicator.model.vo;

import com.graphinsight.indicator.auto.entity.Department;
import com.graphinsight.indicator.auto.entity.User;
import lombok.Data;

import javax.validation.constraints.NotBlank;
import java.util.ArrayList;
import java.util.List;

/**
 * @Description:
 * @Date: 2021/11/16
 */
@Data
public class MeasureVO extends BaseVO{

    private Integer id;


    /**
     * 指标英文名,对应数仓事实表的指标列名
     */
    @NotBlank(message = "指标英文名不能为空")
    private String enName;

    private String code;

    /**
     * 指标中文名，全局唯一
     */
    @NotBlank(message = "指标中文名不能为空")
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
    private String unit;

    /**
     * 指标口径
     */
    private String caliber;

    /**
     * 指标的业务描述
     */
    @NotBlank(message = "指标业务描述不能为空")
    private String description;

    /**
     * 分类信息
     */
    List<CategoryVO> categoryInfo;

    /**
     * 叶子分类节点ID
     */
    Integer leafCategoryId;

    private List<ModelVO> relatedModel;

    private List<DimensionVO> relatedDimension;

    private User creator;

    private Long createTime;

    private User updater;

    private Long updateTime;

    /**
     * 维度Name名称列列名
     */
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
    private Integer deptLevel;


    /**
     * 是否是北极星指标
     * 0-否 1-是
     */
    private Integer northStar;

    /**
     * 指标表达式
     */
    List<ComplexMeasureBaseVO> measureExpressions = new ArrayList<>();

    private List<String> aliases = new ArrayList<>();
}
