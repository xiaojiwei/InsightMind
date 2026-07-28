package com.graphinsight.indicator.aop;

import com.graphinsight.indicator.constant.IndicatorConstant;
import com.graphinsight.indicator.exception.IndicatorParamNotValidException;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.aspectj.lang.annotation.Pointcut;
import org.springframework.stereotype.Component;

/**
 * @Description: 缓存切面
 * @Date: 2021/11/23
 */
@Component
@Aspect
@Slf4j
public class MeasureMonitorAspect {

    @Pointcut("execution(* com.graphinsight.indicator.controller.MeasureMonitorController.*(..))")
    public void pc(){

    }



    @Before(value = "pc()")
    public void check() {
        if (!IndicatorConstant.MEASURE_MONITOR_ENABLE){
            throw IndicatorParamNotValidException.error("不支持该功能");
        }
    }


}
