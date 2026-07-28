package com.graphinsight.indicator.validator;

import com.graphinsight.indicator.annotation.DecisionTreeNodeCheck;
import com.graphinsight.indicator.auto.service.IDecisionTreeService;
import com.graphinsight.indicator.enums.DecisionTreeNodeType;
import com.graphinsight.indicator.model.vo.DecisionTreeFrontNodeType;
import com.graphinsight.indicator.model.vo.DecisionTreeNode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;

import javax.validation.ConstraintValidator;
import javax.validation.ConstraintValidatorContext;
import java.util.List;
import java.util.Objects;

/**
 * Date: 2022/4/14
 * Desc:
 */
@Slf4j
@Component
public class DecisionTreeNodeValidator implements ConstraintValidator<DecisionTreeNodeCheck, DecisionTreeNode> {

    @Autowired
    IDecisionTreeService decisionTreeService;

    @Override
    public boolean isValid(DecisionTreeNode decisionTreeNode, ConstraintValidatorContext context) {
        return check(decisionTreeNode,context);
    }

    private boolean check(DecisionTreeNode decisionTreeNode, ConstraintValidatorContext context){
        boolean result = true;
        DecisionTreeNodeType decisionTreeNodeType = DecisionTreeFrontNodeType.convert(DecisionTreeFrontNodeType.getType(decisionTreeNode.getNodeType()),decisionTreeNode.getNodeData().getNodeCode());
        context.disableDefaultConstraintViolation();
        if (Objects.isNull(decisionTreeNodeType)){
            context.buildConstraintViolationWithTemplate("节点类型不能为空").addConstraintViolation();
            return false;
        }
        if (! DecisionTreeNodeType.supportType(decisionTreeNodeType)){
            context.buildConstraintViolationWithTemplate("节点类型仅支持指标及加减乘除").addConstraintViolation();
            return false;
        }
        switch (decisionTreeNodeType){
            case MEASURE:
                List<DecisionTreeNode> children = decisionTreeNode.getChildren();
                if (!CollectionUtils.isEmpty(children)){
                    DecisionTreeNodeType operatorType = null;
                    if (children.size() < 3){
                        context.buildConstraintViolationWithTemplate("决策树每层至少包含三个节点").addConstraintViolation();
                        return false;
                    }
                    for (DecisionTreeNode node : children) {
                        if (DecisionTreeNodeType.isOperator(DecisionTreeNodeType.getType(node.getNodeType()))){
                            if (Objects.nonNull(operatorType) && !operatorType.getCode().equals(node.getNodeType())){
                                context.buildConstraintViolationWithTemplate("仅支持加减乘除运算符的一种").addConstraintViolation();
                                return false;
                            }
                            operatorType = DecisionTreeNodeType.getType(node.getNodeType());
                        }
                    }
                    if (Objects.isNull(operatorType)){
                        context.buildConstraintViolationWithTemplate("节点必须包含一种运算符").addConstraintViolation();
                        return false;
                    }
                    if (Objects.equals(operatorType,DecisionTreeNodeType.DIVISION) && children.size() != 3){
                        context.buildConstraintViolationWithTemplate("除法运算仅支持【A / B】格式").addConstraintViolation();
                        return false;
                    }
                }
                if (children != null){
                    for (DecisionTreeNode child : children) {
                        if (Objects.equals(child.getNodeType(), DecisionTreeNodeType.MEASURE)){
                            result = check(child, context);
                        }
                    }
                }

        }
        return result;
    }

}
