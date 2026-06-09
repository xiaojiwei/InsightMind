package com.graphinsight.indicator.configuration;

import com.graphinsight.indicator.constant.TokenConstant;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ViewControllerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import springfox.documentation.builders.PathSelectors;
import springfox.documentation.builders.RequestHandlerSelectors;
import springfox.documentation.service.ApiInfo;
import springfox.documentation.service.ApiKey;
import springfox.documentation.service.AuthorizationScope;
import springfox.documentation.service.Contact;
import springfox.documentation.service.Parameter;
import springfox.documentation.service.SecurityReference;
import springfox.documentation.spi.DocumentationType;
import springfox.documentation.spi.service.contexts.SecurityContext;
import springfox.documentation.spring.web.plugins.Docket;
import springfox.documentation.swagger2.annotations.EnableSwagger2;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
/**
 * @Author: lixiaolong
 * @Description: Swagger配置
 * @Date: 2021/11/16
 */
@Configuration
@EnableSwagger2
public class SwaggerConfiguration implements WebMvcConfigurer {
    @Value("${swagger.enabled:true}")
    private boolean enableSwagger;

    @Bean
    public Docket docket() {

        List<Parameter> parameters = new ArrayList<>();
        // parameters.add(new ParameterBuilder()
        //         .name("authorization")
        //         .description("认证token")
        //         .modelRef(new ModelRef("string"))
        //         .parameterType("header")
        //         .required(false)
        //         .build());
        return new Docket(DocumentationType.SWAGGER_2)
                .enable(enableSwagger)
                .apiInfo(apiInfo())
                .select()
                .apis(RequestHandlerSelectors.basePackage("com.graphinsight.indicator"))
                .paths(PathSelectors.any())
                .build()
                // .globalOperationParameters(parameters)
                .securityContexts(Arrays.asList(securityContexts()))
                .securitySchemes(unifiedAuth());
    }

    @Override
    public void addViewControllers(ViewControllerRegistry registry) {
        registry.addRedirectViewController("/v2/api-docs", "/v2/api-docs");
        registry.addRedirectViewController("/swagger-resources/configuration/ui", "/swagger-resources/configuration/ui");
        registry.addRedirectViewController("/swagger-resources/configuration/security", "/swagger-resources/configuration/security");
        registry.addRedirectViewController("/swagger-resources", "/swagger-resources");
    }

    private SecurityContext securityContexts() {
        return SecurityContext.builder()
                .securityReferences(defaultAuth())
                .forPaths(PathSelectors.any())
                .build();
    }

    private List<SecurityReference> defaultAuth() {
        AuthorizationScope authorizationScope = new AuthorizationScope("global", "描述信息");
        AuthorizationScope[] authorizationScopes = new AuthorizationScope[1];
        authorizationScopes[0] = authorizationScope;
        return Arrays.asList(new SecurityReference(TokenConstant.TOKEN_HEADER_STRING, authorizationScopes));
    }

    private static List<ApiKey> unifiedAuth() {
        List<ApiKey> arrayList = new ArrayList();
        arrayList.add(new ApiKey(TokenConstant.TOKEN_HEADER_STRING, TokenConstant.TOKEN_HEADER_STRING, "header"));
        return arrayList;
    }
    /**
     * 配置swagger2信息 =apiInfo
     * @return
     */
    public ApiInfo apiInfo(){
        /*作者信息*/
        Contact contact = new Contact("indicator", "xxx", "xxxx");
        return new ApiInfo(
                "指标平台的API接口",
                "indicator接口",
                "V1.0",
                "urn:toVs",
                contact,
                "Apache 2.0",
                "http://www.apache.org/licenses/LICENSE-2.0",
                new ArrayList());
    }
}
