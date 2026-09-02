package com.graphinsight.indicator.model.vo;

import lombok.Data;

import javax.validation.constraints.NotBlank;
import java.math.BigDecimal;

/**
 * Date: 2022/6/20
 * Desc:
 */
@Data
public class DecisionTreeNodeData {
    /**
     * 节点唯一标识
     * 指标：指标code
     * 常数：常数值
     * 运算符：+ - * /
     */
    @NotBlank(message = "节点code不能为空")
    private String nodeCode;

    /**
     * 节点名称
     * 指标：指标名称
     * 常数：常数值
     * 运算符：+ - * /
     */
    private String nodeName;

    private DecisionTreeContributionInfo contributionInfo;

    private boolean hasAuth = true;


    private String dimCode;

    private String currentDate;

    private String baseDate;

    private BigDecimal previousPeriodValue;

    private BigDecimal currentPeriodValue;

    private boolean drillDown;

}
