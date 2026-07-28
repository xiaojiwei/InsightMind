package com.graphinsight.indicator.auto.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serializable;

/**
 * <p>
 * 维度和维表的关联表
 * </p>
 *
 * @since 2021-11-18
 */
@Data
@EqualsAndHashCode(callSuper = false)
public class DimensionDimtableConnect extends BaseEntity implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 主键
     */
    @TableId(value = "id", type = IdType.AUTO)
    private Integer id;

    /**
     * 维度ID
     */
    private Integer dimId;

    /**
     * 数仓Schema
     */
    private String schemaName;

    /**
     * 维表表名
     */
    private String dimTableName;

    /**
     * 维度唯一列列名
     */
    private String dimPrimaryKey;

    /**
     * 维度Name名称列列名
     */
    private String dimValueColumn;

    /**
     * where条件
     */
    private String whereCondition;

}
