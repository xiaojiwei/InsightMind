package com.graphinsight.indicator.annotation;

import com.graphinsight.indicator.validator.LeafCategoryValidator;

import javax.validation.Constraint;
import javax.validation.Payload;
import javax.validation.constraints.NotNull;
import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Repeatable;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target({ElementType.FIELD})
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Constraint(validatedBy = {LeafCategoryValidator.class})
public @interface LeafCategoryId {
    String message() default "该分类下有子分类";
    Class<?>[] groups() default {};
    Class<? extends Payload>[] payload() default {};
}
