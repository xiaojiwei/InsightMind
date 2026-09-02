package com.graphinsight.indicator.model.vo;

import com.graphinsight.indicator.annotation.LeafCategoryId;
import lombok.Data;

import javax.validation.constraints.NotBlank;

/**
 * @Description:
 * @Date: 2021/11/16
 */
@Data
public class DimensionCreateVO extends BaseVO{


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

//    /**
//     * 是否在线 1-下线 0-下线
//     */
//    private Integer online = 1;

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
     * 维度备注
     */
    private String remark;

    @LeafCategoryId
    private Integer leafCategoryId;

    /**
     * 维度开发负责人
     */
    @NotBlank(message = "开发负责人不能为空")
    private String developer;
}
