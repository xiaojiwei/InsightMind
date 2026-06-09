package com.graphinsight.indicator.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.graphinsight.indicator.auto.entity.AiSearchContext;
import com.graphinsight.indicator.auto.entity.AiSearchInfo;
import com.graphinsight.indicator.auto.entity.AiUserCollect;
import com.graphinsight.indicator.auto.entity.User;
import com.graphinsight.indicator.auto.mapper.*;

import com.graphinsight.indicator.model.vo.*;

import com.graphinsight.indicator.service.*;
import com.graphinsight.indicator.util.UserThreadLocalUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Configuration
@EnableScheduling
@Slf4j
public class AiSearchServiceImpl implements AiSearchService {


    @Autowired
    AiUserCollectMapper aiUserCollectMapper;

    @Autowired
    AiSearchInfoMapper aiSearchInfoMapper;


    @Autowired
    AiSearchContextMapper aiSearchContextMapper;

    @Autowired
    AiSearchDefaultInfoMapper aiSearchDefaultInfoMapper;

    @Override
    public IPage<AiCollectInfoVo> userCollect(Integer pageNum, Integer limit) {

        User user = UserThreadLocalUtil.get();
        SearchAiQueryVO searchAiQueryVO = new SearchAiQueryVO();

        searchAiQueryVO.setUserId(user.getId().toString());

        Page<AiCollectInfoVo> page = new Page<>(pageNum, limit);
        Map<String, Object> paramsMap = new HashMap<>();

        IPage<AiCollectInfoVo> res = aiUserCollectMapper.getListByUserId(page, user.getId().toString());
        if (res.getTotal() == 0) {
            res = aiSearchDefaultInfoMapper.getListByType(page, 99);
        }

        return res;

    }

    @Override
    public IPage<AiSearchInfo> userHistory(Integer pageNum, Integer limit) {

        User user = UserThreadLocalUtil.get();
        Page<AiSearchInfo> page = new Page<>(pageNum, limit);

        IPage<AiSearchInfo> searchInfoList = aiSearchInfoMapper.getListByUserId(page, user.getId().toString());

        return searchInfoList;
    }

    @Override
    public IPage<AiSearchInfo> userHistoryList(AiSessionVo aiSessionVo) {

        User user = UserThreadLocalUtil.get();
        Page<AiSearchInfo> page = new Page<>(aiSessionVo.getPageNo(), aiSessionVo.getPageSize());

        IPage<AiSearchInfo> searchInfoList = aiSearchInfoMapper.getListByUser(page, user.getUsername());

        return searchInfoList;
    }

    @Override
    public void userHistoryDel(Integer seatchId) {
        User user = UserThreadLocalUtil.get();
        LambdaQueryWrapper<AiSearchInfo> queryInfo = new QueryWrapper<AiSearchInfo>().lambda();
        queryInfo.eq(AiSearchInfo::getUserId, user.getId().toString()).eq(AiSearchInfo::getId, seatchId);
        AiSearchInfo aiSearchInfo = new AiSearchInfo();
        aiSearchInfo.setIsDel(1);
        aiSearchInfoMapper.update(aiSearchInfo, queryInfo);
    }

    @Override
    public void cancelCollect(Integer seatchId) {

        LambdaQueryWrapper<AiUserCollect> query = new QueryWrapper<AiUserCollect>().lambda();
        query.eq(AiUserCollect::getUserId, UserThreadLocalUtil.getUserId()).eq(AiUserCollect::getSearchId, seatchId);

        AiUserCollect aiUserCollect = new AiUserCollect();
        aiUserCollect.setIsDel(1);
        aiUserCollectMapper.update(aiUserCollect, query);
    }

    @Override
    public void userCollectOperate(Integer searchId, Integer opType, String contentCode) {
        if (searchId == 0) {
            return;
        }
        User user = UserThreadLocalUtil.get();
        AiSearchInfo aiSearchInfo = aiSearchInfoMapper.selectById(searchId);
        contentCode = aiSearchInfo.getContentCode();
        LambdaQueryWrapper<AiUserCollect> query = new QueryWrapper<AiUserCollect>().lambda();
        query.eq(AiUserCollect::getUserId, user.getId().toString()).eq(AiUserCollect::getContentCode, contentCode);

        List<AiUserCollect> collectList = aiUserCollectMapper.selectList(query);
        if (collectList.isEmpty() && opType == 0) {

            AiUserCollect aiUserCollect = new AiUserCollect();
            aiUserCollect.setUserId(user.getId().toString());
            aiUserCollect.setSearchId(searchId);
            aiUserCollect.setContentCode(contentCode);
            aiUserCollect.setIsDel(0);
            aiUserCollectMapper.insert(aiUserCollect);
        } else {
            AiUserCollect aiUserCollect = new AiUserCollect();
            aiUserCollect.setIsDel(opType);
            aiUserCollectMapper.update(aiUserCollect, query);
        }


    }

    public UUID generateUUIDFromString(String name) {
        // 将字符串转换为字节数组
        byte[] nameBytes = name.getBytes(StandardCharsets.UTF_8);
        // 使用基于MD5哈希的方法生成UUID
        return UUID.nameUUIDFromBytes(nameBytes);
    }

    @Override
    public List<AiSearchInfo> searchInfoRecommend() {
        User user = UserThreadLocalUtil.get();
        LambdaQueryWrapper<AiSearchInfo> queryInfo = new QueryWrapper<AiSearchInfo>().lambda();
        queryInfo.eq(AiSearchInfo::getIsDel, 0).ne(AiSearchInfo::getUserId, user.getId().toString());

        List<AiSearchInfo> searchInfoList = aiSearchInfoMapper.gerRecommend(user.getId().toString());
        return searchInfoList;
    }

    @Override
    public IPage<AiSearchInfo> searchHot(Integer viewType, Integer pageNum, Integer limit) {
        User user = UserThreadLocalUtil.get();
        Page<AiSearchInfo> page = new Page<>(pageNum, limit);
        IPage<AiSearchInfo> searchInfoList = null;
        if (viewType == 0 || viewType == 1) {
            searchInfoList = aiSearchInfoMapper.gerHotInterpret(page, user.getId().toString(), viewType);
        } else {
            searchInfoList = aiSearchInfoMapper.getHotListByUserId(page, user.getId().toString(), viewType);
        }

        if (searchInfoList.getTotal() == 0) {
            searchInfoList = aiSearchDefaultInfoMapper.getListInfoByType(page, viewType);
        }

        return searchInfoList;
    }


    public void recordInfo(String word, Integer userId) {
        AiSearchInfo aiSearchInfo = new AiSearchInfo();
        aiSearchInfo.setIsDel(0);
        aiSearchInfo.setContent(word);
        aiSearchInfo.setUserId(userId.toString());
        aiSearchInfoMapper.insert(aiSearchInfo);

    }

    public void contextSave(AiContextInfoVo aiContextInfoVo) {

        LambdaQueryWrapper<AiSearchContext> query = new QueryWrapper<AiSearchContext>().lambda();
        query.eq(AiSearchContext::getSearchId, aiContextInfoVo.getSearchId());

        List<AiSearchContext> collectList = aiSearchContextMapper.selectList(query);
        if (collectList.isEmpty()) {
            AiSearchContext aiSearchContext = new AiSearchContext();
            aiSearchContext.setSearchId(aiContextInfoVo.getSearchId());
            aiSearchContext.setContentContext(aiContextInfoVo.getTextInfo());
            aiSearchContextMapper.insert(aiSearchContext);
        } else {
            AiSearchContext aiSearchContextUp = new AiSearchContext();
            aiSearchContextUp.setId(collectList.get(0).getId());
            aiSearchContextUp.setContentContext(aiContextInfoVo.getTextInfo());
            aiSearchContextMapper.updateById(aiSearchContextUp);
        }
    }
}
