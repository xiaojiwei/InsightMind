package com.graphinsight.indicator.aop;

import com.alibaba.fastjson.JSON;
import com.graphinsight.indicator.annotation.IgnoreWebLog;
import com.graphinsight.indicator.model.Response;
import com.graphinsight.indicator.util.UserThreadLocalUtil;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Pointcut;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.stereotype.Component;

import java.lang.reflect.Method;
import java.util.Objects;

/**
 * @Description: 日志切面
 * @Date: 2021/11/16
 */
@Component
@Aspect
@Slf4j
public class LogAspect {

    @Pointcut("execution(* com.graphinsight.indicator.controller..*(..))")
    private void log(){}

    @Around("log()")
    public Object webLog(ProceedingJoinPoint point){
        String methodName = point.getSignature().toShortString();
        Object[] args = null;
        try {
            MethodSignature signature = (MethodSignature) point.getSignature();
            Method method = signature.getMethod();
            if (Objects.isNull(method.getAnnotation(IgnoreWebLog.class))){
                args = point.getArgs();
                try {
                    log.info("{} start , params : {}", methodName, JSON.toJSONString(args));
                } catch (Exception ex) {
                    log.info("(序列化json异常, ex:", ex, args);
                }

            }
            Object result = null;
            try {
                result = point.proceed();
            } catch (Throwable throwable) {
                log.error("method: [{}] execute error,request user:[{}],param:[{}] exception : ",
                        methodName, UserThreadLocalUtil.getUserName(), JSON.toJSONString(args),throwable);
                return Response.error(throwable.getMessage());
            }
            try {
                if (Objects.isNull(method.getAnnotation(IgnoreWebLog.class)))
                    log.info("{} end , result : {}",methodName,JSON.toJSONString(result));
            } catch (Exception e) {
                log.error("序列化json异常,e:",e);
            }
            return result;
        } catch (Throwable throwable) {
            log.error("method: [{}] write log error,user:[{}] exception : ", methodName, UserThreadLocalUtil.getUserName(), throwable);
            return Response.error(throwable.getMessage());
        }
    }

}
