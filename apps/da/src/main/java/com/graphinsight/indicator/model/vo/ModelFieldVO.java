package com.graphinsight.indicator.model.vo;

import com.graphinsight.indicator.enums.SqlAggFunType;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

import java.util.List;
import java.util.Set;

/**
 * @Author: lixiaolong
 * @Description: 模型字段信息
 * @Date: 2021/11/22
 */
@Data
public class ModelFieldVO extends BaseVO{


    @ApiModelProperty(value = "字段主键",example = "100")
    private Integer id;

    @ApiModelProperty(value = "字段英文名",required = true)
    private String enName;

    @ApiModelProperty(value = "字段中文名",required = true,example = "订单总量",notes = "指标字段中文名")
    private String cnName;

    @ApiModelProperty(value = "字段属性 1-指标 2-维度")
    private Integer type;

    private String code;

    /**
     * 是否能被删除
     */
    private Boolean deletable;

    @ApiModelProperty(value = "字段类型")
    private Set<String> dataType;

    @ApiModelProperty(value = "数据表")
    private List<String> tableNames;

    @ApiModelProperty(value = "指标计算函数")
    private SqlAggFunType sqlAggFunType;

//    /**
//     * 是否在线 1-下线 0-下线
//     */
//    private Integer online = 1;

//    /**
//     * 是否可拖拽查询 1-可以 0-不可以
//     */
//    private Integer dragable = 1;

    /**
     * 指标单位
     */
    @ApiModelProperty(value = "指标单位",required = false,example = "万元",notes = "指标展示的单位")
    private String unit;

    /**
     * 指标口径
     */
    @ApiModelProperty(value = "指标口径",example = "所有订单数量总和",notes = "指标的统计口径")
    private String caliber;

    /**
     * 指标的业务描述
     */
    @ApiModelProperty(value = "业务描述",required = true,example = "所有订单数量总和")
    private String description;

    List<CategoryVO> categoryInfo;

    private Integer leafCategoryId;

    private String creator;

    private String updator;

    @ApiModelProperty(value = "维度类型")
    private Integer viewType;

    @ApiModelProperty(value = "表字段名")
    private String columnName;

    @ApiModelProperty(value = "维度应用信息")
    private List<DimensionApplicationVO> dimensionExpressions;

    @ApiModelProperty(value = "指标计算表达式")
    private List<ComplexMeasureBaseVO> measureExpressions;
}
