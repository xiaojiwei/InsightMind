package com.graphinsight.indicator.model.vo;

import com.graphinsight.indicator.annotation.LeafCategoryId;
import lombok.Data;

import javax.validation.constraints.NotNull;
import java.util.List;

/**
 * @Description:
 * @Date: 2021/11/16
 */
@Data
public class DimensionUpdateVO extends BaseVO {
    @NotNull(message = "ID不能为空")
    private Integer id;

    /**
     * 维度英文名,对应数仓事实表的维度列名
     */
    private String enName;

    /**
     * 维度中文名，全局唯一
     */
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
    private String description;

    /**
     * 分类ID
     */
    @LeafCategoryId
    private Integer leafCategoryId;

    /**
     * 0-无维表；1-有维表
     */
    @NotNull(message = "维度类型不能为空")
    private Integer frontDimType;

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


    private Integer viewType;

    private String remark;

    private String developer;

    /**
     * 是否是超维
     */
    private Integer isHyper;


    private List<LevelVO> levels;


    private String whereCondition;

}
