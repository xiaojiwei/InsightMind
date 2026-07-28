package com.graphinsight.indicator.auto.entity;

import java.io.Serializable;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * <p>
 * 数仓物理表的列信息
 * </p>
 *
 * @since 2021-11-16
 */
@Data
@EqualsAndHashCode(callSuper = false)
public class DwColumn implements Serializable {

    private static final long serialVersionUID = 1L;


    /**
     * 主键
     */
    @TableId(value = "id", type = IdType.AUTO)
    private Integer id;

    /**
     * 表ID
     */
    private Integer dwTableId;

    /**
     * 字段名，用这个字段去匹配指标、维度英文名
     */
    private String name;

    /**
     * 数据类型
     */
    private String dataType;

    /**
     * 字段描述
     */
    private String description;


}
