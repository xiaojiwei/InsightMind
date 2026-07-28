package com.graphinsight.indicator.model.vo;

import com.graphinsight.indicator.enums.DismantlingConfigTreeCalUnitType;
import com.graphinsight.indicator.enums.OperatorType;
import lombok.Data;
import org.apache.commons.lang.SerializationUtils;
import org.springframework.util.CollectionUtils;

import java.math.BigDecimal;
import java.util.LinkedList;
import java.util.List;
import java.util.Objects;

/**
 * Date: 2022/11/9
 * Desc:
 */
@Data
public class DismantlingTreeNodeCombo extends DismantlingTreeNode {

    List<DismantlingTreeNode> items = new LinkedList<>();

    List<DismantlingTreeNode> convertItems = new LinkedList<>();

    private OperatorType operatorType;

    private DismantlingTreeNodeData nodeData;


    @Override
    public String getDisplayName() {
        String displayname = "";
        if (CollectionUtils.isEmpty(convertItems)) {
            for (DismantlingTreeNode item : items) {
                displayname += item.getDisplayName() + " ";
            }
            return "[ " + displayname + "]";
        } else {
            for (DismantlingTreeNode item : convertItems) {
                displayname += item.getDisplayName() + " ";
            }
            return "[ " + displayname + "]";
        }
    }

    @Override
    public void convert() {

        convertItems.clear();
        boolean needReciprocal = false;
        boolean needReconciliation = false;
        for (DismantlingTreeNode item : items) {
            DismantlingTreeNode convertItem = (DismantlingTreeNode) SerializationUtils.clone(item);
            if (DismantlingConfigTreeCalUnitType.PROPORTION.equals(convertItem.getNodeType())) {
                // 如果节点是占比，当前值需要取占比
                convertItem.getNodeData().setCurrentPeriodValue(convertItem.getNodeData().getCurrentProportion());
                ;
                convertItem.getNodeData().setBasePeriodValue(convertItem.getNodeData().getBaseProportion());
                ;
            }
            if (!item.isOperand()) {
                OperatorType operatorType = item.getOperatorType();
                if (Objects.equals(operatorType, OperatorType.DIVISION)) {
                    // 除法变乘法，并增加倒数标识
                    convertItem.setOperatorType(OperatorType.MULTIPLICATION);
                    convertItem.setDisplayName("*");
                    needReciprocal = true;
                } else if (Objects.equals(operatorType, OperatorType.SUBTRACTION)) {
                    // 减法变加法，并增加取反标识
                    convertItem.setDisplayName("+");
                    needReconciliation = true;
                }
            } else {
                if (needReciprocal) {
                    // 取倒数
                    DismantlingTreeNodeData reciprocalNodeData = item.reciprocalNodeData();
                    convertItem.setNodeData(reciprocalNodeData);
                    convertItem.setDisplayName("1 / " + convertItem.getDisplayName());
                    needReciprocal = false;
                } else if (needReconciliation) {
                    // 取相反数
                    DismantlingTreeNodeData reconciliationNodeData = item.reconciliationNodeData();
                    convertItem.setNodeData(reconciliationNodeData);
                    convertItem.setDisplayName("- " + convertItem.getDisplayName());
                    needReciprocal = false;
                }
            }
            convertItems.add(convertItem);
        }
        this.nodeData = reCalNodeData();

    }

    public DismantlingTreeNodeData reCalNodeData() {
        DismantlingTreeNodeData newNodeData = new DismantlingTreeNodeData();
        DismantlingTreeNode firstNode = items.stream().filter(i -> !DismantlingConfigTreeCalUnitType.OPERATOR.equals(i.getNodeType())).findFirst().orElse(null);
        DismantlingTreeNode firstOperator = items.stream().filter(i -> DismantlingConfigTreeCalUnitType.OPERATOR.equals(i.getNodeType())).findFirst().orElse(null);
        if (firstNode != null) {
            newNodeData.setUpperLayerBasePeriodValue(firstNode.getNodeData().getUpperLayerBasePeriodValue());
            newNodeData.setUpperLayerCurrentPeriodValue(firstNode.getNodeData().getUpperLayerCurrentPeriodValue());
        }
        BigDecimal current;
        BigDecimal base;
        if (Objects.nonNull(firstOperator) && (Objects.equals(firstOperator.getOperatorType(), OperatorType.ADDITION) || Objects.equals(firstOperator.getOperatorType(), OperatorType.ADDITION))) {
            current = convertItems.stream()
                    .filter(item -> !Objects.equals(item.getNodeType(), DismantlingConfigTreeCalUnitType.OPERATOR))
                    .map(item -> item.getNodeData().getCurrentPeriodValue())
                    .reduce(BigDecimal::add).orElse(BigDecimal.ZERO);
            base = convertItems.stream()
                    .filter(item -> !Objects.equals(item.getNodeType(), DismantlingConfigTreeCalUnitType.OPERATOR))
                    .map(item -> item.getNodeData().getBasePeriodValue())
                    .reduce(BigDecimal::add).orElse(BigDecimal.ZERO);
        } else {
            current = convertItems.stream()
                    .filter(item -> !Objects.equals(item.getNodeType(), DismantlingConfigTreeCalUnitType.OPERATOR))
                    .map(item -> item.getNodeData().getCurrentPeriodValue())
                    .reduce(BigDecimal::multiply).orElse(BigDecimal.ZERO);
            base = convertItems.stream()
                    .filter(item -> !Objects.equals(item.getNodeType(), DismantlingConfigTreeCalUnitType.OPERATOR))
                    .map(item -> item.getNodeData().getBasePeriodValue())
                    .reduce(BigDecimal::multiply).orElse(BigDecimal.ZERO);
        }

        newNodeData.setCurrentPeriodValue(current);
        newNodeData.setBasePeriodValue(base);
        for (DismantlingTreeNode node : convertItems) {
            node.getNodeData().setUpperLayerCurrentPeriodValue(newNodeData.getCurrentPeriodValue());
            node.getNodeData().setUpperLayerBasePeriodValue(newNodeData.getBasePeriodValue());
        }
        return newNodeData;
    }
}
