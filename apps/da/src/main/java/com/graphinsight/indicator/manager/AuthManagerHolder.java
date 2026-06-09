package com.graphinsight.indicator.manager;

import com.graphinsight.indicator.enums.AuthMoudleType;
import com.graphinsight.indicator.service.IndicatorAuthService;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;

/**
 * Author: lixiaolong
 * Date: 2022/11/28
 * Desc:
 */
@Component
public class AuthManagerHolder {

    @Resource
    PortalAuthManager portalAuthManager;

    public IndicatorAuthService getService(AuthMoudleType moudleType){
        switch (moudleType){
            case PORTAL:
                return portalAuthManager;
            default:
                return null;
        }
    }

}
