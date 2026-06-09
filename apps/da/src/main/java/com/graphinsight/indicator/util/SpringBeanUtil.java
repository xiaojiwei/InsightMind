package com.graphinsight.indicator.util;

import org.springframework.beans.BeansException;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * Author: lixiaolong
 * Date: 2/8/3
 * Desc:
 */
@Component
public class SpringBeanUtil implements ApplicationContextAware {

    private static ApplicationContext ctx;

    @Override
    public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
        ctx = applicationContext;
    }

    public static <T> T getBean(Class<T> clazz){
       return ctx.getBean(clazz);
    }


    public static <T> Map<String, T> getBeansOfType(Class<T> clazz){
        return ctx.getBeansOfType(clazz);
    }
}
