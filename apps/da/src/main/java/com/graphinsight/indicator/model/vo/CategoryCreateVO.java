package com.graphinsight.indicator.model.vo;

import lombok.Data;

/**
 * @Description:
 * @Date: 2021/12/2
 */
@Data
public class CategoryCreateVO extends BaseVO{

    private static final long serialVersionUID = 1L;

    /**
     * 父分类id
     */
    private Integer parentId;

    /**
     * 所属一级分类ID
     */
    private Integer rootId;

    /**
     * 分类名称
     */
    private String name;

    /**
     * 排序
     */
    private Integer sqeuence;

    /**
     * 是否适用于指标
     */
    private Byte measApplicable = 1;
    /**
     * 是否适用于模型
     */
    private Byte modelApplicable = 1;

    /**
     * 适用于维度
     */
    private Byte dimApplicable = 1;


    private Integer id;

}
