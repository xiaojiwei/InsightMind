package com.graphinsight.indicator.configuration;

import com.graphinsight.indicator.interceptor.AuthenticationInterceptor;
import com.graphinsight.indicator.interceptor.CurrentUserMethodArgumentResolver;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurationSupport;

import java.util.List;

/**
 * @Author: lixiaolong
 * @Description:
 * @Date: 2021/12/6
 */
@Configuration
public class WebConfiguration extends WebMvcConfigurationSupport {

    /**
     * 登录校验拦截器
     *
     * @return
     */
    @Bean
    public AuthenticationInterceptor loginRequiredInterceptor() {
        return new AuthenticationInterceptor();
    }

    /**
     * 去掉添加拦截器的操作
     * @param registry
     */
    @Override
    protected void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(loginRequiredInterceptor())
                .addPathPatterns("/**")
                .excludePathPatterns("/swagger-ui.html**")
                .excludePathPatterns("/static/**")
                .excludePathPatterns("/swagger-resources/**")
                .excludePathPatterns("/webjars/**")
                .excludePathPatterns("/error")
                .excludePathPatterns("/monitoring","/monitoring/**")
                .excludePathPatterns("/test/**")
                .excludePathPatterns("/secret/**")
                .excludePathPatterns("/indicator/api/v1/**")
                .excludePathPatterns("/cache/**","/cache/reloadCache")
                .excludePathPatterns("/category/create","/category/delete/*")
                .excludePathPatterns("/dayu/**")
                .excludePathPatterns("/login/**","/login/coa/**");
//                .excludePathPatterns("/agent/ai/**");
        super.addInterceptors(registry);
    }

    /**
     * CurrentUser 注解参数解析器
     *
     * @return
     */
    @Bean
    public CurrentUserMethodArgumentResolver currentUserMethodArgumentResolver() {
        return new CurrentUserMethodArgumentResolver();
    }

    /**
     * 参数解析器
     *
     * @param argumentResolvers
     */
    @Override
    protected void addArgumentResolvers(List<HandlerMethodArgumentResolver> argumentResolvers) {
        argumentResolvers.add(currentUserMethodArgumentResolver());
        super.addArgumentResolvers(argumentResolvers);
    }

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        registry.addResourceHandler("/swagger-ui.html**").addResourceLocations("classpath:/META-INF/resources/swagger-ui.html");
        registry.addResourceHandler("/webjars/**").addResourceLocations("classpath:/META-INF/resources/webjars/");
        registry.addResourceHandler("/static/**").addResourceLocations("classpath:/static/");
    }

}
