package com.graphinsight.indicator.model.vo;

import io.swagger.annotations.ApiModelProperty;
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

    @ApiModelProperty(value = "指标贡献度信息")
    private DecisionTreeContributionInfo contributionInfo;

    @ApiModelProperty(value = "是否有指标权限,如果没有,贡献度信息、指标统计信息统一显示星号 : * ")
    private boolean hasAuth = true;


    private String dimCode;

    private String currentDate;

    private String baseDate;

    private BigDecimal previousPeriodValue;

    private BigDecimal currentPeriodValue;

    private boolean drillDown;

}
