package com.graphinsight.indicator.util;

import com.graphinsight.indicator.model.vo.AiVUserVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class IDaaSValidateUtilAi {

    public String info(String tokenInfo) {
        return "dev_user";
    }

    public AiVUserVO IDaaSUserInfo(String tokenInfo) {
        AiVUserVO vo = new AiVUserVO();
        vo.setLdap_name("dev_user");
        vo.setNickname("开发用户");
        return vo;
    }

    public String validate(String accessToken, String scope) {
        return "dev_user";
    }
}
