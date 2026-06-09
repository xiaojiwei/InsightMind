package com.graphinsight.indicator.validator;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.graphinsight.indicator.annotation.DecisionTreeCreateCheck;
import com.graphinsight.indicator.auto.entity.DecisionTree;
import com.graphinsight.indicator.auto.service.IDecisionTreeService;
import com.graphinsight.indicator.model.vo.DecisionTreeVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import javax.validation.ConstraintValidator;
import javax.validation.ConstraintValidatorContext;
import java.util.Objects;

/**
 * Author: lixiaolong
 * Date: 2022/4/14
 * Desc:
 */
@Slf4j
@Component
public class DecisionTreeCreateValidator implements ConstraintValidator<DecisionTreeCreateCheck, DecisionTreeVO> {

    @Autowired
    IDecisionTreeService decisionTreeService;

    @Override
    public boolean isValid(DecisionTreeVO decisionTreeVO, ConstraintValidatorContext context) {
        context.disableDefaultConstraintViolation();
        // 树名校验
        if (Objects.nonNull(decisionTreeVO.getId())){
            // 更新
            DecisionTree decisionTree = decisionTreeService.getOne(Wrappers.<DecisionTree>lambdaQuery().eq(DecisionTree::getName, decisionTreeVO.getTreeName()));
            if (Objects.nonNull(decisionTree) && ! Objects.equals(decisionTree.getId(),decisionTreeVO.getId())){
                context.buildConstraintViolationWithTemplate("决策树名重复").addConstraintViolation();
                return false;
            }
        } else {
            // 新增
            DecisionTree decisionTree = decisionTreeService.getOne(Wrappers.<DecisionTree>lambdaQuery().eq(DecisionTree::getName, decisionTreeVO.getTreeName()));
            if (Objects.nonNull(decisionTree)){
                context.buildConstraintViolationWithTemplate("决策树名重复").addConstraintViolation();
                return false;
            }
        }
        return true;
    }
}
