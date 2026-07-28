package com.graphinsight.indicator.validator;

import com.graphinsight.indicator.annotation.LeafCategoryId;
import com.graphinsight.indicator.manager.CategoryManager;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import javax.validation.ConstraintValidator;
import javax.validation.ConstraintValidatorContext;
import java.util.Objects;

/**
 * @Description:
 * @Date: 2021/12/9
 */
@Slf4j
@Component
public class LeafCategoryValidator implements ConstraintValidator<LeafCategoryId,Integer> {

    @Autowired
    CategoryManager categoryManager;

    @Override
    public void initialize(LeafCategoryId constraintAnnotation) {

    }

    @Override
    public boolean isValid(Integer leafCategroyId, ConstraintValidatorContext constraintValidatorContext) {
        if(Objects.isNull(leafCategroyId)){
            return true;
        }
        if(categoryManager.hasChildCategory(leafCategroyId))
            return false;
        return true;
    }
}
