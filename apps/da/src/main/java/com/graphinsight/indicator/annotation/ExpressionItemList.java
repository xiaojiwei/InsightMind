package com.graphinsight.indicator.annotation;

import com.graphinsight.indicator.validator.ExpressionItemValidator;

import javax.validation.Constraint;
import javax.validation.Payload;
import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target({ElementType.FIELD})
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Constraint(validatedBy = {ExpressionItemValidator.class})
public @interface ExpressionItemList {
    String message() default "指标表达式不合法";
    Class<?>[] groups() default {};
    Class<? extends Payload>[] payload() default {};
}
