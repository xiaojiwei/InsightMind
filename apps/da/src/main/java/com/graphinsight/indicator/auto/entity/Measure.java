package com.graphinsight.indicator.auto.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * <p>
 * 指标表
 * </p>
 *
 * @since 2021-11-16
 */
@Data
@EqualsAndHashCode(callSuper = false)
public class Measure extends BaseEntityV3 implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 主键
     */
    @TableId(value = "id", type = IdType.AUTO)
    private Integer id;

    /**
     * 指标英文名,对应数仓事实表的指标列名
     */
    private String enName;

    /**
     * 指标编码，展示用，由平台生成，全局唯一，比如meas_xxx，代表这个指标
     */
    private String code;

    /**
     * 指标中文名，全局唯一
     */
    private String cnName;

    /**
     * 是否在线 1-上线 0-下线
     */
    private Integer online;

    /**
     * 指标单位
     */
    private String unit;

    /**
     * 指标口径
     */
    private String caliber;

    private String definition;

    /**
     * 指标的业务描述
     */
    private String description;

    /**
     * 叶子结点分类ID
     */
    private Integer leafCategoryId;

    /**
     * 备注
     */
    private String remark;

    /**
     * 部门ID
     */
    private Integer departmentId;

    /**
     * 是否被删除 0-否 1-是
     */
    private Integer isDelete;

    /**
     * 是否是北极星指标
     * 0-否 1-是
     */
    private Integer northStar;

    /**
     * 维度开发人
     */
    private Integer developer;

    /**
     * 维度负责人
     */
    private Integer owner;


    private String ownerUser;


    private String developUser;

    private String offlineRemark;

    private String offlineOperator;
    private String functionType;


    private LocalDateTime offlineTime = LocalDateTime.now();


}
