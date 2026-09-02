package com.graphinsight.indicator.model.vo;

import com.graphinsight.indicator.annotation.LeafCategoryId;
import lombok.Data;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;

/**
 * @Description:
 * @Date: 2021/11/16
 */
@Data
public class MeasureCreateVO extends BaseVO{

    /**
     * 指标英文名,对应数仓事实表的指标列名
     */
    @NotBlank(message = "指标英文名不能为空")
    private String enName;


    /**
     * 指标中文名，全局唯一
     */
    @NotBlank(message = "指标中文名不能为空")
    private String cnName;

//    /**
//     * 是否在线 1-下线 0-下线
//     */
//    private Integer online = 1;

    /**
     * 是否可拖拽查询 1-可以 0-不可以
     */
    private Integer draggable;

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
     * 维度备注
     */
    private String remark;

    /**
     * 叶子分类节点ID
     */
    @NotNull(message = "分类为空")
    @LeafCategoryId
    Integer leafCategoryId;

    /**
     * 部门ID
     */
    Integer departmentId;

    private String ownerUser;


    private String developUser;

}
