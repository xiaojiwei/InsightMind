package com.graphinsight.indicator.validator;

import com.graphinsight.indicator.annotation.ExpressionItemList;
import com.graphinsight.indicator.enums.ItemType;
import com.graphinsight.indicator.manager.MeasureManager;
import com.graphinsight.indicator.model.vo.ExpressionItem;
import com.graphinsight.indicator.model.vo.MeasureBasicInfoVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import javax.validation.ConstraintValidator;
import javax.validation.ConstraintValidatorContext;
import java.util.LinkedList;
import java.util.Objects;

/**
 * Date: 2022/4/14
 * Desc:
 */
@Slf4j
@Component
public class ExpressionItemValidator implements ConstraintValidator<ExpressionItemList,  LinkedList<ExpressionItem>> {

    @Autowired
    MeasureManager measureManager;

    @Override
    public boolean isValid(LinkedList<ExpressionItem> expressionItemList, ConstraintValidatorContext context) {
        context.disableDefaultConstraintViolation();;
        if (!CollectionUtils.isEmpty(expressionItemList)){
            for (ExpressionItem item : expressionItemList) {
                String type = item.getOperatingType();
                if(ItemType.OPERATOR.getName().equalsIgnoreCase(type)){
                    if(!StringUtils.hasLength(item.getOperator())){
                        context.buildConstraintViolationWithTemplate("操作符不能为空").addConstraintViolation();
                        return false;
                    }

                } else if(ItemType.OPERAND.getName().equalsIgnoreCase(type)){
                    if(Objects.isNull(item.getOperand())){
                        context.buildConstraintViolationWithTemplate("操作数不能为空").addConstraintViolation();
                    }

                    MeasureBasicInfoVO operand = item.getOperand();
                    boolean available = measureManager.available(operand.getId());
                    if(!available){
                        context.buildConstraintViolationWithTemplate("指标ID: " + operand.getId() + "不可用,不能用来创建指标").addConstraintViolation();
                        return false;
                    }
                } else if(ItemType.CONSTANT.getName().equalsIgnoreCase(type)){
                    if(Objects.isNull(item.getConstant())){
                        context.buildConstraintViolationWithTemplate("常数项不能为空").addConstraintViolation();
                        return false;
                    }
                } else {
                    context.buildConstraintViolationWithTemplate("操作类型不合法").addConstraintViolation();
                    return false;
                }
            }
        }
        return true;

    }
}
