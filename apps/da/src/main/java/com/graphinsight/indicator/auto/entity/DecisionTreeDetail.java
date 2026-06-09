package com.graphinsight.indicator.auto.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serializable;

/**
 * <p>
 * 决策树详情表
 * </p>
 *
 * @author lixiaolong5
 * @since 2022-06-13
 */
@Data
@EqualsAndHashCode(callSuper = false)
public class DecisionTreeDetail implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 主键
     */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /**
     * 决策树表主键
     */
    private Long treeId;

    /**
     * 节点code 节点类型是指标的话保存指标code
     */
    private String nodeValue;

    /**
     * 节点类型 0-指标 1-维度 2-加号 3-减号 4-乘号 5-除号 
     */
    private Integer nodeType;

    /**
     * 当前节点的父节点 父节点不可能是计算节点 跟节点的parent_code 是null tree_level 是0
     */
    private String parentCode;


    /**
     * 节点在同一个树高中的顺序
     */
    private Integer treeLevelSeq;


}
