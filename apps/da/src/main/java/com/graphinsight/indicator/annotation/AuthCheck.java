package com.graphinsight.indicator.annotation;

import com.graphinsight.indicator.enums.AuthMoudleType;
import com.graphinsight.indicator.enums.IndicatorAuthType;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.METHOD})
public @interface AuthCheck {

    IndicatorAuthType authType() default IndicatorAuthType.READ_ONLY;

    AuthMoudleType moudleType() default AuthMoudleType.PORTAL;

    boolean superAdminEnable() default true;
}
