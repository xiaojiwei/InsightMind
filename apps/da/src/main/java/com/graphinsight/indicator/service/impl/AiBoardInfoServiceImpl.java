package com.graphinsight.indicator.service.impl;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import cn.hutool.core.bean.BeanUtil;
import com.graphinsight.indicator.auto.entity.User;
import com.graphinsight.indicator.manager.UserManager;
import com.graphinsight.indicator.model.vo.AiBusinessDelVo;
import com.graphinsight.indicator.service.AiBoardInfoService;
import com.graphinsight.indicator.model.vo.AiBoardInfoPageParam;
import com.graphinsight.indicator.model.vo.AiBoardInfoVO;
import com.graphinsight.indicator.auto.entity.AiBoardInfo;
import com.graphinsight.indicator.auto.mapper.AiBoardInfoMapper;
import com.graphinsight.indicator.service.wordNlp.WordDictService;
import com.graphinsight.indicator.util.UserThreadLocalUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;

/**
 * @author houfenglei
 */
@Service
public class AiBoardInfoServiceImpl extends ServiceImpl<AiBoardInfoMapper, AiBoardInfo> implements AiBoardInfoService {

    @Autowired
    UserManager userManager;
    @Autowired
    WordDictService wordDictService;

    @Override
    public IPage<AiBoardInfoVO> page(AiBoardInfoPageParam pageParam) {

        pageParam.setKeyWord(pageParam.getKeyWord().trim());
        Page<AiBoardInfo> page = new Page<>(pageParam.getPageNo(), pageParam.getPageSize());

        IPage<AiBoardInfoVO> pageInfo = getBaseMapper().selectPageInfo(page, pageParam);


        List<String> userList = new ArrayList<>();
        pageInfo.getRecords().forEach(item -> {
            userList.add(item.getUpdater());
        });
        Map<String, User> userMap = userManager.getUserMapByUsernames(userList);

        pageInfo.getRecords().forEach(item -> {

            if (null != userMap.get(item.getUpdater())) {
                item.setUpdaterUser(userMap.get(item.getUpdater()));
            }
        });

        return pageInfo;
    }

    @Override
    public void update(AiBoardInfoVO VO) {

        List<AiBoardInfo> aiBoardInfoEntities = getBaseMapper().selectList(Wrappers.<AiBoardInfo>lambdaQuery()
                .ne(AiBoardInfo::getId, VO.getId()).eq(AiBoardInfo::getBoardName, VO.getBoardName()).eq(AiBoardInfo::getIsDel, 0));
        if (!aiBoardInfoEntities.isEmpty()) {
            throw new RuntimeException("已存在相同的关键字，不允许");
        }
        AiBoardInfo toUpdate = new AiBoardInfo();
        toUpdate.setId(VO.getId());
        toUpdate.setBoardName(VO.getBoardName());
        toUpdate.setBoardUrl(VO.getBoardUrl());
        toUpdate.setUpdater(UserThreadLocalUtil.getUserName());
        toUpdate.setUpdateDate(new Date());
        super.updateById(toUpdate);
        wordDictService.init();
    }

    @Override
    public void save(AiBoardInfoVO VO) {
        AiBoardInfo toSave = BeanUtil.copyProperties(VO, AiBoardInfo.class);
        List<AiBoardInfo> aiBoardInfoEntities = getBaseMapper().selectList(Wrappers.<AiBoardInfo>lambdaQuery().eq(AiBoardInfo::getBoardName, VO.getBoardName()).eq(AiBoardInfo::getIsDel, 0));
        if (!aiBoardInfoEntities.isEmpty()) {
            throw new RuntimeException("已存在相同的关键字，不允许");
        }
        toSave.setCreateDate(new Date());
        toSave.setUpdateDate(new Date());
        toSave.setCreator(UserThreadLocalUtil.getUserName());
        toSave.setUpdater(UserThreadLocalUtil.getUserName());
        super.save(toSave);
        wordDictService.init();
    }

    @Override
    public void delete(AiBusinessDelVo aiBoardDelVO) {

        AiBoardInfo aiBoardInfo = new AiBoardInfo();
        aiBoardInfo.setIsDel(1);
        getBaseMapper().update(aiBoardInfo, Wrappers.<AiBoardInfo>lambdaUpdate().eq(AiBoardInfo::getIsDel, 0).in(AiBoardInfo::getId, aiBoardDelVO.getIdList()));
        wordDictService.init();
    }
}