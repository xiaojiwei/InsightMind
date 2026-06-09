package com.graphinsight.indicator.auto.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serializable;

/**
 * <p>
 * 数仓物理表
 * </p>
 *
 * @author lixiaolong
 * @since 2021-11-16
 */
@Data
@EqualsAndHashCode(callSuper = false)
public class DwTable extends BaseEntityV3 implements Serializable {

    private static final long serialVersionUID = 1L;
    /**
     * 主键
     */
    @TableId(value = "id", type = IdType.AUTO)
    private Integer id;


    /**
     * 100-Doris,101-TiDB,102-MySQL
     */
    private Integer sourceType;

    /**
     * 库名
     */
    private String schemaName;

    /**
     * 表名
     */
    private String tableName;

    /**
     * 0-事实表；1-维度表
     */
    private Integer type;

    /**
     * 是否在线 1-下线 0-下线
     */
    private Integer online;

    /**
     * 模型英文名
     */
    private String enName;

    /**
     * 模型中文名
     */
    private String cnName;


    /**
     * 备注
     */
    private String remark;

    /**
     * 表描述
     */
    private String description;

    /**
     * 叶子节点分类ID
     */
    private Integer leafCategoryId;

    /**
     * 加工方式
     * 0-增量 1-全量
     * 是否需要卡dt
     * 0-否 1-是
     */
    private Integer hasDt;


    /**
     * 是否是聚合表
     * 0-否 1-是
     */
    private Integer aggregationTable;

    /**
     * 事实表类型
     * 0-聚合表
     * 1-明细表
     */
    private Integer factTableType;

    /**
     * 模型开发负责人
     */
    private String developer;


    private String offlineRemark;

    private String offlineOperator;

    private String tableDetailName;
}
