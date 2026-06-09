package com.graphinsight.indicator.auto.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * <p>
 * 分类表
 * </p>
 *
 * @author lixiaolong
 * @since 2021-11-16
 */
@Data
@EqualsAndHashCode(callSuper = false)
public class Category implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 主键
     */
    @TableId(value = "id", type = IdType.AUTO)
    private Integer id;

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
     * 是否适用于指标
     */
    private Byte measApplicable;
    /**
     * 是否适用于模型
     */
    private Byte modelApplicable;

    /**
     * 适用于维度
     */
    private Byte dimApplicable;

    /**
     * 分类描述
     */
    private String description;

    /**
     * 创建人
     */
    private Integer creator;

    /**
     * 更新人
     */
    private Integer updater;

    /**
     * 创建时间
     */
    private LocalDateTime createTime;

    /**
     * 更新时间
     */
    private LocalDateTime updateTime;


    /**
     * 排序字段
     */
    private Integer sequence;

    /**
     * 指标分类CODE
     * 前期是没有的，后面为了方便跟第三方系统交互，增加此字段
     * 由于老数据此字段为空，因此为了兼容，如果code为空，就将主键做一次md5加密，存入到数据库中。新数据统一UUID生成
     */
    private String code;



}
