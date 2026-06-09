package com.graphinsight.indicator.auto.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serializable;

/**
 * <p>
 * 多维分析查询任务列表
 * </p>
 *
 * @author lixiaolong5
 * @since 2022-07-05
 */
@Data
@EqualsAndHashCode(callSuper = false)
public class DimensionAnalysisTask extends BaseEntityV2 implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 主键
     */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /**
     * 指标code
     */
    private String measCode;

    /**
     * 维度code
     */
    private String dimCode;

    /**
     * 本期时间
     */
    private String currentPeriod;

    /**
     * 基期时间
     */
    private String basePeriod;

    /**
     * 报告名称
     */
    private String reportName;

    /**
     * 报告状态0-查询中 1-查询完成 2-查询失败
     */
    private Integer status;

    /**
     * 空间ID
     */
    private Long spaceId;

    /**
     * 当期值
     */
    private String currentValue;

    /**
     * 本期值
     */
    private String baseValue;

    /**
     * 任务执行失败信息
     */
    private String errorMessage;

    /**
     * 贡献度计算类型
     */
    private Integer contributionCalType;


}
