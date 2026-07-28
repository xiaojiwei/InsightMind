package com.graphinsight.indicator.model.vo;

import com.graphinsight.indicator.annotation.LeafCategoryId;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

import javax.validation.constraints.NotNull;
import java.util.List;

/**
 * @Description:
 * @Date: 2021/11/16
 */
@Data
@ApiModel(value = "维度更新视图", description = "更新维度的参数")
public class DimensionUpdateVO extends BaseVO {
    @NotNull(message = "ID不能为空")
    @ApiModelProperty(value = "维度ID", example = "1", required = true)
    private Integer id;

    /**
     * 维度英文名,对应数仓事实表的维度列名
     */
    @ApiModelProperty(value = "维度英文名", example = "city_id", notes = "维度字段名")
    private String enName;

    /**
     * 维度中文名，全局唯一
     */
    @ApiModelProperty(value = "维度中文名", example = "订单总量", notes = "维度字段中文名")
    private String cnName;

//    /**
//     * 是否在线 1-下线 0-下线
//     */
//    private Integer online = 1;

    /**
     * 是否可拖拽查询 1-可以 0-不可以
     */
    private Integer dragable;

    /**
     * 维度的业务描述
     */
    @ApiModelProperty(value = "维度业务描述", example = "所有订单数量总和")
    private String description;

    /**
     * 分类ID
     */
    @LeafCategoryId
    @ApiModelProperty(value = "维度分类ID", example = "1")
    private Integer leafCategoryId;

    /**
     * 0-无维表；1-有维表
     */
    @NotNull(message = "维度类型不能为空")
    @ApiModelProperty(value = "维度类型", example = "2", notes = "0-无维表；1-有维表")
    private Integer frontDimType;

    @ApiModelProperty(value = "维表", example = "nr_sales_dim_dept_info", required = true)
    private String dimTableName;

    @ApiModelProperty(value = "schemaName", example = "eps_test", required = true)
    private String schemaName;

    /**
     * 维度在维表中的列名(group_by的字段名)
     */
    @ApiModelProperty(value = "查询字段", example = "city_id", required = true)
    private String queryField;

    /**
     * 维度Name名称列列名
     */
    @ApiModelProperty(value = "显示字段", example = "city_name", required = true)
    private String displayField;


    private Integer viewType;

    private String remark;

    @ApiModelProperty(value = "开发负责人")
    private String developer;

    /**
     * 是否是超维
     */
    private Integer isHyper;


    @ApiModelProperty(value = "级别信息")
    private List<LevelVO> levels;


    @ApiModelProperty(value = "where条件")
    private String whereCondition;

}
