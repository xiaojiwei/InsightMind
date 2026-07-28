package com.graphinsight.indicator.model.vo;

import com.graphinsight.indicator.auto.entity.Dimension;
import com.graphinsight.indicator.enums.DismantlingConfigTreeCalUnitType;
import com.graphinsight.indicator.enums.OperatorType;
import lombok.Data;
import org.springframework.beans.BeanUtils;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Date: 2022/11/4
 * Desc:
 */
@Data
public class DismantlingTreeNode implements Serializable {

    public DismantlingTreeNode() {
    }

    public DismantlingTreeNode(String displayName, OperatorType operatorType, DismantlingConfigTreeCalUnitType nodeType) {
        this.displayName = displayName;
        this.operatorType = operatorType;
        this.nodeType = nodeType;
    }

    /**
     * 是否展示给用户
     */
    public Boolean display = true;


    public Boolean isCombo = false;


    /**
     * 本节点是否是比率型指标
     */
    private Boolean isRatio = false;


    /**
     * 上层节点是否是比率型指标
     */
    private Boolean parentRatio = false;

    /**
     * 下钻的维值集合
     * key: dimCode
     * value: dimValue
     */
    private Map<String, String> dimensionValueMap = new LinkedHashMap<>();

    /**
     * 下钻维度集合
     */
    private List<Dimension> drillDowmDimensions = new LinkedList<>();

    /**
     * 维值列表
     */
    private List<String> dimensionValues = new LinkedList<>();

    /**
     * 当前指标Code
     */
    private String code;

    /**
     * 节点显示的名称
     * 如果是运算符节点，就是加减乘除符号
     */
    private String displayName;


    /**
     * 当前节点指纹
     */
    private String fingerprints;

    /**
     * 父节点指纹
     */
    private String parentfingerPrints;

    /**
     * 节点数据
     */
    private DismantlingTreeNodeData nodeData = new DismantlingTreeNodeData();

    /**
     * 运算符
     */
    private OperatorType operatorType;

    /**
     * 是否需要转成倒数
     * 如果是操作数，且前面跟的是除号，值就是true
     */
    private Boolean reciprocal = false;

    /**
     * 是否需要转成倒数
     * 如果是操作数，且前面跟的是减号，值就是true
     */
    private Boolean reconciliation = false;

    /**
     * 节点类型
     */
    private DismantlingConfigTreeCalUnitType nodeType;

    public String getDisplayName() {
        return displayName;
    }

    /**
     * 取相反数
     *
     * @return
     */
    public DismantlingTreeNodeData reconciliationNodeData() {
        DismantlingTreeNodeData result = new DismantlingTreeNodeData();
        BigDecimal currentPeriodValue = nodeData.getCurrentPeriodValue();
        BigDecimal currentRecon = BigDecimal.ZERO.subtract(currentPeriodValue);

        BigDecimal basePeriodValue = nodeData.getBasePeriodValue();
        BigDecimal baseRecon = BigDecimal.ZERO.subtract(basePeriodValue);

        BeanUtils.copyProperties(nodeData, result);
        result.setCurrentPeriodValue(currentRecon);
        result.setBasePeriodValue(baseRecon);
        return result;
    }

    /**
     * 取倒数
     *
     * @return
     */
    public DismantlingTreeNodeData reciprocalNodeData() {
        DismantlingTreeNodeData result = new DismantlingTreeNodeData();
        BigDecimal currentPeriodValue = nodeData.getCurrentPeriodValue();
        BigDecimal currentRecon = null;

        if (BigDecimal.ZERO.compareTo(currentPeriodValue) != 0) {
            currentRecon = BigDecimal.ONE.divide(currentPeriodValue, 16, BigDecimal.ROUND_DOWN);
        }

        BigDecimal baseRecon = null;
        BigDecimal basePeriodValue = nodeData.getBasePeriodValue();
        if (BigDecimal.ZERO.compareTo(basePeriodValue) != 0) {
            baseRecon = BigDecimal.ONE.divide(basePeriodValue, 16, BigDecimal.ROUND_DOWN);
        }
        BeanUtils.copyProperties(nodeData, result);
        result.setCurrentPeriodValue(currentRecon == null ? BigDecimal.ZERO : currentRecon);
        result.setBasePeriodValue(baseRecon == null ? BigDecimal.ZERO : baseRecon);
        return result;
    }

    /**
     * 子节点
     */
    private List<DismantlingTreeNode> children = new LinkedList<>();

    public boolean isOperand() {
        return !Objects.equals(nodeType, DismantlingConfigTreeCalUnitType.OPERATOR);
    }

    public int priority() {
        if (Objects.equals(nodeType, DismantlingConfigTreeCalUnitType.OPERATOR)) {
            switch (operatorType) {
                case PLACEHOLDER:
                    return 0;
                case EMPTY:
                    return 3;
                case SUBTRACTION:
                case ADDITION:
                    return 1;
                case DIVISION:
                case MULTIPLICATION:
                    return 2;
            }
        }
        return 0;
    }


    public void convert() {

    }

}
