package com.graphinsight.indicator.auto.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * <p>
 * 维度表
 * </p>
 *
 * @author lixiaolong
 * @since 2021-11-16
 */
@Data
@EqualsAndHashCode(callSuper = false)
public class Dimension extends BaseEntityV3 implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 主键
     */
    @TableId(value = "id", type = IdType.AUTO)
    private Integer id;

    /**
     * 维度英文名,对应数仓维表的唯一列，也是数仓事实表中维度外键
     */
    private String enName;

    /**
     * 维度编码，展示用，由平台生成，全局唯一，比如dim_xxx，代表这个维度
     */
    private String code;

    /**
     * 维度中文名称，全局唯一
     */
    private String cnName;

    /**
     * 别名
     */
    private String caption;

    /**
     * 显示类型 0 字符；1 日；2 周；3 月；4 季；5 年；6 小时
     */
    private Integer viewType;

    /**
     * 0-退化维；1-标准维无维表；2-标准为有维表
     */
    private Integer dimType;

    /**
     * 定义
     */
    private String defintion;

    /**
     * 描述
     */
    private String description;

    /**
     * 叶子分类ID
     */
    private Integer leafCategoryId;

    /**
     * 备注
     */
    private String remark;

    /**
     * 是否在线 1-上线 0-下线
     */
    private Integer online = 1;

    /**
     * 是否被删除 0-否 1-是
     */
    private Integer isDelete;

    /**
     * 维度开发负责人
     */
    private String developer;

    /**
     * 是否是超维
     */
    private Integer isHyper;



    private String offlineRemark;

    private String offlineOperator;

    private LocalDateTime offlineTime = LocalDateTime.now();


}
