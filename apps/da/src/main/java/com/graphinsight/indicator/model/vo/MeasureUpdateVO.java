package com.graphinsight.indicator.model.vo;

import com.graphinsight.indicator.annotation.LeafCategoryId;
import com.graphinsight.indicator.auto.entity.Department;
import com.graphinsight.indicator.auto.entity.User;
import lombok.Data;

import javax.validation.constraints.NotNull;
import java.util.ArrayList;
import java.util.List;

/**
 * @Description:
 * @Date: 2021/11/16
 */
@Data
public class MeasureUpdateVO extends BaseVO{

    @NotNull
    private Integer id;

    /**
     * 指标英文名,对应数仓事实表的指标列名
     */
    private String enName;


    /**
     * 指标中文名，全局唯一
     */
    private String cnName;

//    /**
//     * 是否在线 1-下线 0-下线
//     */
//    private Integer online = 1;

    /**
     * 是否可拖拽查询 1-可以 0-不可以
     */
    private Integer dragable = 0;

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

    /**
     * 一级分类ID
     */
    @LeafCategoryId
    private Integer leafCategoryId;


    private Integer draggable;

    /**
     * 部门ID
     */
    Department department;

    /**
     * 是否是北极星指标
     * 0-否 1-是
     */
    private Integer northStar;

    /**
     * 指标开发者
     */
    private User developer;

    /**
     * 指标负责人
     */
    private User owner;

    private String ownerUser;


    private String developUser;

    // 别名
    private List<String> aliases = new ArrayList<>();

}
