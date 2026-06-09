package com.graphinsight.indicator.service.impl;

import com.alibaba.fastjson.JSON;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.graphinsight.indicator.auto.entity.*;
import com.graphinsight.indicator.auto.mapper.*;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.graphinsight.indicator.enums.YesNoType;
import com.graphinsight.indicator.model.PageData;
import com.graphinsight.indicator.model.vo.*;
import com.graphinsight.indicator.service.AiSearchService;
import com.graphinsight.indicator.service.AiSessionService;
import com.graphinsight.indicator.util.StringUtil;
import com.graphinsight.indicator.util.UserThreadLocalUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

@Configuration
@EnableScheduling
@Slf4j
public class AiSessionServiceImpl extends ServiceImpl<AiSessionInfoMapper, AiSessionInfo> implements AiSessionService {


    @Override
    public AiSessionInfo createSession(AiSessionCreateVo aiSessionVo) {
        AiSessionInfo sessionInfo = new AiSessionInfo();
        sessionInfo.initCreate();
        sessionInfo.setName(aiSessionVo.getName());
        sessionInfo.setType(aiSessionVo.getType());
        save(sessionInfo);
        // 保存场景的信息
        if (!Objects.equals(aiSessionVo.getSceneId(), null) && !Objects.equals(aiSessionVo.getSceneContent(), null)) {
            AiSearchInfo aiSearchInfoSystem = new AiSearchInfo();
            aiSearchInfoSystem.setIsDel(0);
            aiSearchInfoSystem.setAnalysisType(0);
            if (aiSessionVo.getSceneContent() instanceof String) {
                aiSearchInfoSystem.setContent(aiSessionVo.getSceneContent());
            } else {
                aiSearchInfoSystem.setContent(JSON.toJSONString(aiSessionVo.getSceneContent()));
            }
            aiSearchInfoSystem.setContentCode(StringUtil.generateUUIDFromString("word tip system").toString().replaceAll("-", ""));
            aiSearchInfoSystem.setUserId(UserThreadLocalUtil.getUserId().toString());
            aiSearchInfoSystem.setUser(UserThreadLocalUtil.getUserName());
            aiSearchInfoSystem.setSessionId(sessionInfo.getId());
            aiSearchInfoSystem.setRoleType("system");
            aiSearchInfoMapper.insert(aiSearchInfoSystem);

        }
        return sessionInfo;
    }

    @Override
    public AiSessionInfo updateSession(AiSessionUpdateVo aiSessionUpdateVo) {
        AiSessionInfo sessionInfo = new AiSessionInfo();
        sessionInfo.initUpdate();
        sessionInfo.setName(aiSessionUpdateVo.getName());
        update(sessionInfo, new QueryWrapper<AiSessionInfo>().lambda().eq(AiSessionInfo::getCreator, UserThreadLocalUtil.getUserName()).eq(AiSessionInfo::getId, aiSessionUpdateVo.getSessionId()));
        return sessionInfo;
    }


    @Override
    public AiSessionInfo delSession(Integer searchId) {
        AiSessionInfo sessionInfo = new AiSessionInfo();
        sessionInfo.initUpdate();
        sessionInfo.setIsDel(YesNoType.YES.getCode());
        update(sessionInfo, new QueryWrapper<AiSessionInfo>().lambda().eq(AiSessionInfo::getCreator, UserThreadLocalUtil.getUserName()).eq(AiSessionInfo::getId, searchId));
        return sessionInfo;
    }

    @Override
    public IPage<AiSessionInfo> listSession(AiSessionVo aiSessionVo) {

        QueryWrapper<AiSessionInfo> queryWrapper = new QueryWrapper<>();

        queryWrapper.lambda().eq(AiSessionInfo::getCreator, UserThreadLocalUtil.getUserName())
                .eq(AiSessionInfo::getIsDel, YesNoType.NO.getCode())
                .ne(AiSessionInfo::getName, "")
                .like(!StringUtil.isEmpty(aiSessionVo.getKeyword()), AiSessionInfo::getName, aiSessionVo.getKeyword())
                .eq(!StringUtil.isEmpty(aiSessionVo.getType()), AiSessionInfo::getType, aiSessionVo.getType())
                .orderByDesc(AiSessionInfo::getCreateTime);

        Page<AiSessionInfo> page = new Page<>(aiSessionVo.getPageNo(), aiSessionVo.getPageSize());

        IPage<AiSessionInfo> sessionInfos = getBaseMapper().selectPage(page, queryWrapper);

        return sessionInfos;
    }

    @Autowired
    AiSearchInfoMapper aiSearchInfoMapper;

    @Override
    public IPage<AiSearchInfo> getSessionDetail(AiSessionDetailVo aiSessionDetailVo) {


        Page<AiSearchInfo> page = new Page<>(aiSessionDetailVo.getPageNo(), aiSessionDetailVo.getPageSize());

        IPage<AiSearchInfo> aiSearchInfoPage = aiSearchInfoMapper.selectPage(page, new QueryWrapper<AiSearchInfo>().lambda()
                .eq(AiSearchInfo::getIsDel, YesNoType.NO.getCode())
                .eq(AiSearchInfo::getSessionId, aiSessionDetailVo.getSessionId())
                .eq(AiSearchInfo::getUser, UserThreadLocalUtil.getUserName())
                .orderByDesc(AiSearchInfo::getId));

        aiSearchInfoPage.getRecords().sort(Comparator.comparingInt(AiSearchInfo::getId));
        aiSearchInfoPage.getRecords().forEach(aiSearchInfo -> {
            aiSearchInfo.setContent(JSON.parseObject(aiSearchInfo.getContent().toString()));
        });
        return aiSearchInfoPage;
    }

    @Override
    public void createContent(AiContentCreateVo aiContentCreateVo) {
        AiSearchInfo aiSearchInfo = new AiSearchInfo();
        BeanUtils.copyProperties(aiContentCreateVo, aiSearchInfo);
        aiSearchInfo.setAnalysisType(0);
        aiSearchInfo.setContent(JSON.toJSONString(aiContentCreateVo.getContent()));
        aiSearchInfo.setUser(UserThreadLocalUtil.getUserName());
        aiSearchInfo.setUserId(UserThreadLocalUtil.getUserId().toString());
        aiSearchInfoMapper.insert(aiSearchInfo);
    }
}
