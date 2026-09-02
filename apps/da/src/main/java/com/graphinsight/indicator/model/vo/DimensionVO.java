package com.graphinsight.indicator.model.vo;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.graphinsight.indicator.auto.entity.User;
import lombok.Data;

import javax.validation.constraints.NotBlank;
import java.util.List;

/**
 * @Description:
 * @Date: 2021/11/16
 */
@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class DimensionVO extends BaseVO{

    private String code;

    private Integer id;

    /**
     * 维度英文名,对应数仓事实表的维度列名
     */
    @NotBlank(message = "维度英文名不能为空")
    private String enName;


    /**
     * 维度中文名，全局唯一
     */
    @NotBlank(message = "维度中文名不能为空")
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
    private String description;

    /**
     * 分类信息
     */
    List<CategoryVO> categoryInfo;

    private List<DimensionApplicationVO> relatedModel;

    private List<MeasureVO> relatedMeasure;

    /**
     * 叶子分类节点ID
     */
    Integer leafCategoryId;

//    @NotNull(message = "viewType不能为空")
    private Integer viewType = 0;

    private User creator;

    private User developer;

    private Long createTime;

    private User updater;

    private Long updateTime;

    private String dimTableName;

    private String schemaName;

    /**
     * 维度在维表中的列名(group_by的字段名)
     */
    private String queryField;

    /**
     * 维度Name名称列列名
     */
    private String displayField;

    private String whereCondition;

    /**
     * 维度Name名称列列名
     */
    private String remark;

    private Integer frontDimType;

    private Integer dimType;

    /**
     * 是否是超维
     */
    private Integer isHyper;



    private List<LevelVO> levels;
}
