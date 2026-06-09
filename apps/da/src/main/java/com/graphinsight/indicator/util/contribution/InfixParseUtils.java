package com.graphinsight.indicator.util.contribution;

import com.graphinsight.indicator.enums.DismantlingConfigTreeCalUnitType;
import com.graphinsight.indicator.enums.OperatorType;
import com.graphinsight.indicator.model.vo.DismantlingTreeNode;
import com.graphinsight.indicator.model.vo.DismantlingTreeNodeCombo;
import org.springframework.util.CollectionUtils;

import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Stack;
import java.util.stream.Collectors;

/**
 * Author: lixiaolong
 * Date: 2022/11/8
 * Desc: 中缀表达式解析
 */
public class InfixParseUtils {

    private static DismantlingTreeNode fillInNode(OperatorType type) {
        DismantlingTreeNode node = new DismantlingTreeNode();
        node.setOperatorType(type);
        node.setNodeType(DismantlingConfigTreeCalUnitType.OPERATOR);
        return node;
    }

    private static OperatorType getFillInOperatorType(List<DismantlingTreeNode> nodes) {
        Map<Integer, List<DismantlingTreeNode>> map = nodes.stream().filter(node -> !node.isOperand()).collect(Collectors.groupingBy(DismantlingTreeNode::priority));
        // 优先级最低的node
        DismantlingTreeNode dismantlingTreeNode = nodes.stream().filter(node -> !node.isOperand()).sorted(Comparator.comparing(DismantlingTreeNode::priority)).findFirst().orElse(null);
        if (dismantlingTreeNode == null || dismantlingTreeNode.getOperatorType() == null) {
            return OperatorType.PLACEHOLDER;
        }

        OperatorType type = dismantlingTreeNode.getOperatorType();
        Set<Integer> keys = map.keySet();
        if (keys.size() == 1) {
            switch (type) {
            case ADDITION:
            case SUBTRACTION:
                return OperatorType.PLACEHOLDER;
            case DIVISION:
            case MULTIPLICATION:
                return OperatorType.PLACEHOLDER;
            case EMPTY:
                return OperatorType.EMPTY;
            }
        } else if (keys.size() == 2) {
            switch (type) {
            case ADDITION:
            case SUBTRACTION:
                return OperatorType.ADDITION;
            case DIVISION:
            case MULTIPLICATION:
                return OperatorType.PLACEHOLDER;
            case EMPTY:
                return OperatorType.EMPTY;
            }
        }
        return OperatorType.PLACEHOLDER;
    }

    public static List<DismantlingTreeNode> infixParse(List<DismantlingTreeNode> nodes) {
        if (CollectionUtils.isEmpty(nodes)) {
            return nodes;
        }
        Stack<DismantlingTreeNode> operand = new Stack<>();
        Stack<DismantlingTreeNode> operator = new Stack<>();
        // 在结尾先多增加一个最小运算符
        nodes.add(fillInNode(getFillInOperatorType(nodes)));
        nodes.forEach(node -> {
            if (node.isOperand()) {
                // 如果节点是操作数，直接放到操作数栈
                operand.push(node);
            } else {
                // 2 如果节点是操作符
                if (operator.size() == 0) {
                    // 2.1 栈顶元素为空，直接入栈
                    operator.push(node);
                } else {
                    // 2.2 栈顶元素不为空
                    DismantlingTreeNode top = operator.peek();
                    if (node.priority() >= top.priority()) {
                        // 2.2.1 当前操作符优先级大于栈顶元素优先级，操作符入栈
                        operator.push(node);
                    } else {
                        // 当前操作符优先级小于栈顶元素优先级
                        DismantlingTreeNodeCombo combo = new DismantlingTreeNodeCombo();
                        combo.setIsCombo(true);
                        combo.setDisplayName("combo");
                        while (operand.size() > 0 && operator.size() > 0) {
                            // 先从操作数中取出一个数据，再从操作符中取出一个数据，再从操作数中取出一个数据
                            // 循环此操作，直到操作数栈为空，或者操作符栈顶元素的优先级低于当前的优先级. 将取出的元素按照顺序，组合成一个combo，入栈操作数栈。
                            DismantlingTreeNode peek = operator.peek();
                            if (node.priority() < peek.priority()) {
                                DismantlingTreeNode leftNode = operand.pop();
                                DismantlingTreeNode operatorNode = operator.pop();
                                combo.getItems().add(leftNode);
                                combo.getItems().add(operatorNode);
                            } else {
                                break;
                            }
                        }
                        if (operand.size() > 0) {
                            DismantlingTreeNode rightNode = operand.pop();
                            combo.getItems().add(rightNode);
                        }
                        Collections.reverse(combo.getItems());
                        combo.convert();
                        operand.push(combo);
                        operator.push(node);
                    }
                }
            }
        });
        // 把开始加入的补位操作符去掉
        operator.pop();
        nodes.remove(nodes.size() - 1);
        List<DismantlingTreeNode> result = new LinkedList<>();
        // 扫描结束，此时操作符栈中只有加减
        while (operator.size() > 0) {
            DismantlingTreeNode leftNode = operand.pop();
            DismantlingTreeNode operatorNode = operator.pop();
            result.add(leftNode);
            result.add(operatorNode);
        }
        if (operand.size() > 0) {
            DismantlingTreeNode rightNode = operand.pop();
            result.add(rightNode);
        }

        Collections.reverse(result);
        return result;
    }

    public static void main(String[] args) {
        List<DismantlingTreeNode> result = new LinkedList<>();
        DismantlingTreeNode add = new DismantlingTreeNode("+", OperatorType.ADDITION, DismantlingConfigTreeCalUnitType.OPERATOR);
        DismantlingTreeNode sub = new DismantlingTreeNode("-", OperatorType.SUBTRACTION, DismantlingConfigTreeCalUnitType.OPERATOR);
        DismantlingTreeNode mul = new DismantlingTreeNode("*", OperatorType.MULTIPLICATION, DismantlingConfigTreeCalUnitType.OPERATOR);
        DismantlingTreeNode div = new DismantlingTreeNode("/", OperatorType.DIVISION, DismantlingConfigTreeCalUnitType.OPERATOR);

        DismantlingTreeNode a = new DismantlingTreeNode("a", null, DismantlingConfigTreeCalUnitType.NUMERICAL_VALUE);
        DismantlingTreeNode b = new DismantlingTreeNode("b", null, DismantlingConfigTreeCalUnitType.NUMERICAL_VALUE);
        DismantlingTreeNode c = new DismantlingTreeNode("c", null, DismantlingConfigTreeCalUnitType.NUMERICAL_VALUE);
        DismantlingTreeNode d = new DismantlingTreeNode("d", null, DismantlingConfigTreeCalUnitType.NUMERICAL_VALUE);
        DismantlingTreeNode f = new DismantlingTreeNode("f", null, DismantlingConfigTreeCalUnitType.NUMERICAL_VALUE);
        DismantlingTreeNode e = new DismantlingTreeNode("e", null, DismantlingConfigTreeCalUnitType.NUMERICAL_VALUE);

        result.add(a);
        result.add(mul);
        result.add(a);
        result.add(add);
        result.add(b);
        result.add(add);
        result.add(c);
        result.add(mul);
        result.add(d);
        result.add(div);
        result.add(f);
        result.add(new DismantlingTreeNode("-", OperatorType.SUBTRACTION, DismantlingConfigTreeCalUnitType.OPERATOR));
        result.add(e);
        result.add(mul);
        result.add(c);
        result.add(new DismantlingTreeNode("/", OperatorType.DIVISION, DismantlingConfigTreeCalUnitType.OPERATOR));
        result.add(e);
        result.add(new DismantlingTreeNode("/", OperatorType.DIVISION, DismantlingConfigTreeCalUnitType.OPERATOR));
        result.add(e);

        result.add(a);
        result.add(sub);
        result.add(b);
        result.add(add);
        result.add(a);
        result.add(add);
        result.add(b);
        result.add(sub);
        result.add(a);
        result.add(new DismantlingTreeNode("/", OperatorType.DIVISION, DismantlingConfigTreeCalUnitType.OPERATOR));
        result.add(b);
        result.add(add);
        result.add(a);
        result.add(add);
        result.add(b);

        infixParseTest(result);
    }

    public static void infixParseTest(List<DismantlingTreeNode> nodes) {
        Stack<DismantlingTreeNode> operand = new Stack<>();
        Stack<DismantlingTreeNode> operator = new Stack<>();
        nodes.forEach(node -> {
            if (node.isOperand()) {
                // 如果节点是操作数，直接放到操作数栈
                operand.push(node);
            } else {
                // 2 如果节点是操作符
                if (operator.size() == 0) {
                    // 2.1 栈顶元素为空，直接入栈
                    operator.push(node);
                } else {
                    // 2.2 栈顶元素不为空
                    DismantlingTreeNode top = operator.peek();
                    if (node.priority() >= top.priority()) {
                        // 2.2.1 当前操作符优先级大于栈顶元素优先级，操作符入栈
                        operator.push(node);
                    } else {
                        // 当前操作符优先级小于栈顶元素优先级
                        DismantlingTreeNodeCombo combo = new DismantlingTreeNodeCombo();
                        combo.setIsCombo(true);
                        combo.setDisplayName("combo");
                        while (operand.size() > 0 && operator.size() > 0) {
                            // 先从操作数中取出一个数据，再从操作符中取出一个数据，再从操作数中取出一个数据
                            // 循环此操作，直到操作数栈为空，或者操作符栈顶元素的优先级低于当前的优先级. 将取出的元素按照顺序，组合成一个combo，入栈操作数栈。
                            DismantlingTreeNode peek = operator.peek();
                            if (node.priority() < peek.priority()) {
                                DismantlingTreeNode leftNode = operand.pop();
                                DismantlingTreeNode operatorNode = operator.pop();
                                combo.getItems().add(leftNode);
                                combo.getItems().add(operatorNode);
                            } else {
                                break;
                            }
                        }
                        DismantlingTreeNode rightNode = operand.pop();
                        combo.getItems().add(rightNode);
                        Collections.reverse(combo.getItems());
                        operand.push(combo);
                        operator.push(node);
                    }
                }
            }
        });
        List<DismantlingTreeNode> result = new LinkedList<>();
        // 扫描结束，此时操作符栈中只有加减
        while (operator.size() > 0) {
            DismantlingTreeNode leftNode = operand.pop();
            DismantlingTreeNode operatorNode = operator.pop();
            result.add(leftNode);
            result.add(operatorNode);
        }
        DismantlingTreeNode rightNode = operand.pop();
        result.add(rightNode);
        Collections.reverse(result);
        System.out.println("转换之前：");
        result.forEach(node -> {
            System.out.print(node.getDisplayName() + " ");
        });
        System.out.println();
        result.forEach(node -> {
            if (node.getIsCombo()) {
                node.convert();
            }
        });
        System.out.println("转换之后：");
        result.forEach(node -> {
            System.out.print(node.getDisplayName() + " ");
        });
    }

}
