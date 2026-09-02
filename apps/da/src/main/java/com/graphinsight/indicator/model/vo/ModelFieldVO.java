package com.graphinsight.indicator.model.vo;

import com.graphinsight.indicator.enums.SqlAggFunType;
import lombok.Data;

import java.util.List;
import java.util.Set;

/**
 * @Description: 模型字段信息
 * @Date: 2021/11/22
 */
@Data
public class ModelFieldVO extends BaseVO{


    private Integer id;

    private String enName;

    private String cnName;

    private Integer type;

    private String code;

    /**
     * 是否能被删除
     */
    private Boolean deletable;

    private Set<String> dataType;

    private List<String> tableNames;

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
    private String unit;

    /**
     * 指标口径
     */
    private String caliber;

    /**
     * 指标的业务描述
     */
    private String description;

    List<CategoryVO> categoryInfo;

    private Integer leafCategoryId;

    private String creator;

    private String updator;

    private Integer viewType;

    private String columnName;

    private List<DimensionApplicationVO> dimensionExpressions;

    private List<ComplexMeasureBaseVO> measureExpressions;
}
