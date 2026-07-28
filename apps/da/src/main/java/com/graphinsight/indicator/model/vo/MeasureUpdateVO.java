package com.graphinsight.indicator.model.vo;

import com.graphinsight.indicator.annotation.LeafCategoryId;
import com.graphinsight.indicator.auto.entity.Department;
import com.graphinsight.indicator.auto.entity.User;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

import javax.validation.constraints.NotNull;
import java.util.ArrayList;
import java.util.List;

/**
 * @Description:
 * @Date: 2021/11/16
 */
@Data
@ApiModel(value = "MeasureUpdateVO", description = "更新指标的参数")
public class MeasureUpdateVO extends BaseVO{

    @NotNull
    @ApiModelProperty(value = "指标ID",example = "1", required = true)
    private Integer id;

    /**
     * 指标英文名,对应数仓事实表的指标列名
     */
    @ApiModelProperty(value = "指标英文名",example = "order_num",notes = "指标字段名")
    private String enName;


    /**
     * 指标中文名，全局唯一
     */
    @ApiModelProperty(value = "指标中文名",example = "订单总量",notes = "指标字段中文名")
    private String cnName;

//    /**
//     * 是否在线 1-下线 0-下线
//     */
//    private Integer online = 1;

    /**
     * 是否可拖拽查询 1-可以 0-不可以
     */
    @ApiModelProperty(value = "是否可拖拽查询 1-可以 0-不可以",example = "1")
    private Integer dragable = 0;

    /**
     * 指标单位
     */
    @ApiModelProperty(value = "指标中文名",example = "万元",notes = "指标展示的单位")
    private String unit;

    /**
     * 指标口径
     */
    @ApiModelProperty(value = "指标口径",example = "所有订单数量总和",notes = "指标的统计口径")
    private String caliber;

    /**
     * 指标的业务描述
     */
    @ApiModelProperty(value = "指标业务描述",example = "所有订单数量总和")
    private String description;

    /**
     * 一级分类ID
     */
    @LeafCategoryId
    @ApiModelProperty(value = "指标分类ID",example = "1")
    private Integer leafCategoryId;


    private Integer draggable;

    /**
     * 部门ID
     */
    @ApiModelProperty(value = "部门")
    Department department;

    /**
     * 是否是北极星指标
     * 0-否 1-是
     */
    @ApiModelProperty(value = "是否是北极星指标 0-否 1-是")
    private Integer northStar;

    /**
     * 指标开发者
     */
    @ApiModelProperty(value = "开发负责人")
    private User developer;

    /**
     * 指标负责人
     */
    @ApiModelProperty(value = "业务负责人")
    private User owner;

    private String ownerUser;


    private String developUser;

    // 别名
    private List<String> aliases = new ArrayList<>();

}
