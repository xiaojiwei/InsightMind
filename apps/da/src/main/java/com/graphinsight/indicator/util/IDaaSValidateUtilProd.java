package com.graphinsight.indicator.util;

import com.graphinsight.indicator.service.IDaaSValidateService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Slf4j
@Profile("prod")
public class IDaaSValidateUtilProd implements IDaaSValidateService {

    @Override
    public String validate(String accessToken, String scope) throws Exception {
        return "dev_user";
    }
}
