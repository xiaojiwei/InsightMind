package com.graphinsight.indicator.util.contribution.bean;

import com.graphinsight.indicator.enums.ContributionCalculationType;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;

/**
 * Date: 2022/6/14
 * Desc:
 */
@Data
@Builder
public class ContributionCalculationParam {

    /**
     * 比率型指标额外参数
     */
    private RatioMeasureCalculationParam ratioParam;

    /**
     * 上期的值
     */
    private BigDecimal previousPeriodValue;

    /**
     * 本期的值
     */
    private BigDecimal currentPeriodValue;

    /**
     * 除法拆解时 Y = A/B
     * 如果 previousPeriodValue代表A 则该值代表B
     * 如果 previousPeriodValue代表A带表B 则previousPeriodValueB代表A
     */
    private BigDecimal previousPeriodOppositeValue;

    /**
     * 同上
     */
    private BigDecimal currentPeriodOppositeValue;

    /**
     * 上层指标的上期值
     */
    private BigDecimal upperLayerPreviousPeriodValue;

    /**
     * 上层指标的本期值
     */
    private BigDecimal upperLayerCurrentPeriodValue;

    /**
     * 是否是被减数,减法拆解时需要用到
     */
    private boolean minuend;


    /**
     * 是否是被除数,除法拆解时需要用到
     */
    private boolean dividend;


    /**
     * 是否是比率类型的数据
     * 比率类型的数据，在计算幅度的时候不需要除以分母
     */
    private boolean isRatio;

    /**
     * 上层指标是否是比率型指标
     */
    private boolean parentPatio;

    /**
     * 贡献度计算方式
     */
    private ContributionCalculationType contributionCalculationType;


    /**
     * Doris查询条件
     */
    private ContributionOriginQueryParam originQueryParam;

}
