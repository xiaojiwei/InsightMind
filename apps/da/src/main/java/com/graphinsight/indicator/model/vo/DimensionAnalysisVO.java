package com.graphinsight.indicator.model.vo;

import lombok.Data;

/**
 * Author: lixiaolong
 * Date: 2022/7/5
 * Desc:
 */
@Data
public class DimensionAnalysisVO extends BaseVO{
    /**
     * 主键
     */
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
    private String currentDate;

    /**
     * 基期时间
     */
    private String baseDate;

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
     * 进度百分比
     */
    private Integer progress;

}
