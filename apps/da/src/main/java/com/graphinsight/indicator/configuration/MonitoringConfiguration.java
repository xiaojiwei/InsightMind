package com.graphinsight.indicator.configuration;

import net.bull.javamelody.*;
import org.springframework.aop.support.Pointcuts;
import org.springframework.aop.support.annotation.AnnotationMatchingPointcut;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.boot.web.servlet.ServletListenerRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.scheduling.annotation.Schedules;
import org.springframework.stereotype.Controller;
import org.springframework.stereotype.Service;
import org.springframework.web.bind.annotation.RestController;

import javax.servlet.DispatcherType;
import javax.servlet.FilterRegistration;
import javax.servlet.ServletContext;
import java.util.EventListener;
import java.util.Map;

/**
 */
@Configuration
@ConditionalOnWebApplication
public class MonitoringConfiguration {

    private static final String JAVA_MELODY = "javamelody";
    private static final String REGISTRATION_BEAN_NAME = "javamelody-registration";

    @Bean
    public ServletListenerRegistrationBean<EventListener> monitoringSessionListener(
            ServletContext servletContext) {
        final ServletListenerRegistrationBean<EventListener> servletListenerRegistrationBean = new ServletListenerRegistrationBean<>(new SessionListener());
        if (servletContext.getFilterRegistration(JAVA_MELODY) != null) {
            servletListenerRegistrationBean.setEnabled(false);
        }
        return servletListenerRegistrationBean;
    }

    @Bean(name = REGISTRATION_BEAN_NAME)
    public FilterRegistrationBean monitoringFilter(ServletContext servletContext) {
        final FilterRegistrationBean registrationBean = new FilterRegistrationBean();
        final MonitoringFilter filter = new MonitoringFilter();
        filter.setApplicationType("Spring Boot");

        registrationBean.setFilter(filter);
        registrationBean.setAsyncSupported(true);
        registrationBean.setName(JAVA_MELODY);
        registrationBean.addInitParameter(Parameter.AUTHORIZED_USERS.getCode(), "uio:jkl");
        registrationBean.setDispatcherTypes(DispatcherType.REQUEST, DispatcherType.ASYNC);
        registrationBean.addInitParameter(Parameter.URL_EXCLUDE_PATTERN.getCode(), "(/webjars/.*|/css/.*|/images/.*|/fonts/.*|/js/.*)");
        registrationBean.addUrlPatterns("/*");

        final FilterRegistration filterRegistration = servletContext.getFilterRegistration(JAVA_MELODY);
        if (filterRegistration != null) {
            registrationBean.setEnabled(false);
            Map<String, String> map = registrationBean.getInitParameters();
            for (final Map.Entry<String, String> entry : map.entrySet()) {
                filterRegistration.setInitParameter(entry.getKey(), entry.getValue());
            }
        }
        return registrationBean;
    }

    @Bean
    public SpringDataSourceBeanPostProcessor monitoringDataSourceBeanPostProcessor() {
        final SpringDataSourceBeanPostProcessor processor = new SpringDataSourceBeanPostProcessor();
        return processor;
    }

    @Bean
    public MonitoringSpringAdvisor monitoringSpringAdvisor() {
        return new MonitoringSpringAdvisor(new MonitoredWithAnnotationPointcut());
    }

    @Bean
    public MonitoringSpringAdvisor monitoringSpringServiceAdvisor() {
        return new MonitoringSpringAdvisor(new AnnotationMatchingPointcut(Service.class));
    }

    @Bean
    public MonitoringSpringAdvisor monitoringSpringControllerAdvisor() {
        return new MonitoringSpringAdvisor(new AnnotationMatchingPointcut(Controller.class));
    }

    @Bean
    public MonitoringSpringAdvisor monitoringSpringRestControllerAdvisor() {
        return new MonitoringSpringAdvisor(new AnnotationMatchingPointcut(RestController.class));
    }

    @Bean
    public MonitoringSpringAdvisor monitoringSpringAsyncAdvisor() {
        return new MonitoringSpringAdvisor(
                Pointcuts.union(new AnnotationMatchingPointcut(Async.class),
                        new AnnotationMatchingPointcut(null, Async.class)));
    }

    @Bean
    public MonitoringSpringAdvisor monitoringSpringScheduledAdvisor() {
        return new MonitoringSpringAdvisor(
                Pointcuts.union(new AnnotationMatchingPointcut(null, Scheduled.class),
                        new AnnotationMatchingPointcut(null, Schedules.class)));
    }

    @Bean
    public SpringRestTemplateBeanPostProcessor monitoringRestTemplateBeanPostProcessor() {
        return new SpringRestTemplateBeanPostProcessor();
    }

    @Bean
    public SpringContext javamelodySpringContext() {
        return new SpringContext();
    }

}