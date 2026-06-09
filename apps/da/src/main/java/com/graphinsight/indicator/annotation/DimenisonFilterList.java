package com.graphinsight.indicator.annotation;

import com.graphinsight.indicator.validator.DimensionFilterValidator;

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
@Constraint(validatedBy = {DimensionFilterValidator.class})
public @interface DimenisonFilterList {
    String message() default "指标筛选器配置不合法";
    Class<?>[] groups() default {};
    Class<? extends Payload>[] payload() default {};
}
