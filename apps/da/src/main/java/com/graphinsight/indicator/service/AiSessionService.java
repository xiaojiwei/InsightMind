package com.graphinsight.indicator.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.IService;
import com.graphinsight.indicator.auto.entity.AiSearchInfo;
import com.graphinsight.indicator.auto.entity.AiSessionInfo;
import com.graphinsight.indicator.model.vo.*;

import java.util.List;

/**
 * CahceService
 */
public interface AiSessionService extends IService<AiSessionInfo> {

    /**
     * 创建历史会话
     *
     * @param
     * @return
     */
    AiSessionInfo createSession(AiSessionCreateVo aiSessionVo);

    AiSessionInfo updateSession(AiSessionUpdateVo aiSessionUpdateVo);

    AiSessionInfo delSession(Integer searchId);

    IPage<AiSessionInfo> listSession(AiSessionVo aiSessionVo);

    IPage<AiSearchInfo> getSessionDetail(AiSessionDetailVo aiSessionDetailVo);

   void createContent( AiContentCreateVo aiContentCreateVo);
}
