package com.graphinsight.indicator.aop;

import com.graphinsight.indicator.annotation.AuthCheck;
import com.graphinsight.indicator.enums.AuthMoudleType;
import com.graphinsight.indicator.manager.AuthManagerHolder;
import com.graphinsight.indicator.service.AuthService;
import com.graphinsight.indicator.service.IndicatorAuthService;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;

/**
 * @Description: 权限验证切面
 * @Date: 2021/11/16
 */
@Component
@Aspect
@Slf4j
public class AuthCheckAspect {

    @Resource
    AuthService authService;
    @Resource
    AuthManagerHolder authManagerHolder;

    @Before("@annotation(authCheck)")
    public void authCheck(AuthCheck authCheck){
        // 超管免验证
        if (authCheck.superAdminEnable() && authService.isSuperAdmin()){
            return;
        }

        AuthMoudleType authMoudleType = authCheck.moudleType();
        IndicatorAuthService indicatorAuthService = authManagerHolder.getService(authMoudleType);

    }

}
