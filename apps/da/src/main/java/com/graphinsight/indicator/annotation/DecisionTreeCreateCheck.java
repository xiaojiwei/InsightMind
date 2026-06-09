package com.graphinsight.indicator.annotation;

import com.graphinsight.indicator.validator.DecisionTreeCreateValidator;

import javax.validation.Constraint;
import javax.validation.Payload;
import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target({ElementType.FIELD,ElementType.PARAMETER,ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Constraint(validatedBy = {DecisionTreeCreateValidator.class})
public @interface DecisionTreeCreateCheck {
    String message() default "决策树格式不合法";
    Class<?>[] groups() default {};
    Class<? extends Payload>[] payload() default {};
}
