package com.graphinsight.indicator.service.impl;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import cn.hutool.core.bean.BeanUtil;
import com.graphinsight.indicator.auto.entity.User;
import com.graphinsight.indicator.auto.entity.WordValues;
import com.graphinsight.indicator.manager.UserManager;
import com.graphinsight.indicator.model.vo.AiQuestionCountVO;
import com.graphinsight.indicator.service.AiQuestionInfoService;
import com.graphinsight.indicator.model.vo.AiQuestionInfoPageParam;
import com.graphinsight.indicator.model.vo.AiQuestionInfoVO;
import com.graphinsight.indicator.auto.entity.AiQuestionInfo;
import com.graphinsight.indicator.auto.mapper.AiQuestionInfoMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * @author houfenglei
 */
@Service
public class AiQuestionInfoServiceImpl extends ServiceImpl<AiQuestionInfoMapper, AiQuestionInfo> implements AiQuestionInfoService {

    @Autowired
    UserManager userManager;

    @Override
    public IPage<AiQuestionInfoVO> page(AiQuestionInfoPageParam pageParam) {

        pageParam.setKeyWord(pageParam.getKeyWord().trim());
        Page<WordValues> page = new Page<>(pageParam.getPageNo(), pageParam.getPageSize());

        if (pageParam.getEndTime() != null) {
            pageParam.getEndTime().setHours(pageParam.getEndTime().getHours() + (24 - pageParam.getEndTime().getHours()));
        }


        IPage<AiQuestionInfoVO> pageInfo = getBaseMapper().selectPageInfo(page, pageParam);


        List<String> userList = new ArrayList<>();
        pageInfo.getRecords().forEach(item -> {
            userList.add(item.getUser());
        });
        Map<String, User> userMap = userManager.getUserMapByUsernames(userList);

        pageInfo.getRecords().forEach(item -> {
            if (null != userMap.get(item.getUser())) {
                item.setUserInfo(userMap.get(item.getUser()));
            }
        });

        return pageInfo;
    }

    @Override
    public void update(AiQuestionInfoVO VO) {

        AiQuestionInfo toUp = new AiQuestionInfo();
        toUp.setId(VO.getId());
        toUp.setNotes(VO.getNotes());
        super.updateById(toUp);
    }

    @Override
    public void save(AiQuestionInfoVO VO) {
        AiQuestionInfo toSave = BeanUtil.copyProperties(VO, AiQuestionInfo.class);
        super.save(toSave);
    }

    @Override
    public void delete(Long id) {
        super.removeById(id);
    }

    @Override
    public List<AiQuestionCountVO> getCountInfo(AiQuestionInfoPageParam aiBusinessSearchVo) {
        if (aiBusinessSearchVo.getEndTime() != null) {
            aiBusinessSearchVo.getEndTime().setHours(aiBusinessSearchVo.getEndTime().getHours() + (24 - aiBusinessSearchVo.getEndTime().getHours()));
        }
        List<AiQuestionCountVO> countVOList = getBaseMapper().selectCountInfo(aiBusinessSearchVo);

        // 计算总数
        Integer sumCount = countVOList.stream().mapToInt(AiQuestionCountVO::getTotal).sum();

        if (sumCount == 0) {
            AiQuestionCountVO countFailVO = new AiQuestionCountVO();
            countFailVO.setTotal(0);
            countFailVO.setReplyType("fail");
            countVOList.add(countFailVO);

            AiQuestionCountVO countSuccessVO = new AiQuestionCountVO();
            countSuccessVO.setTotal(0);
            countSuccessVO.setReplyType("success");
            countVOList.add(countSuccessVO);
        }

        AiQuestionCountVO countVO = new AiQuestionCountVO();
        countVO.setTotal(sumCount);
        countVO.setReplyType("all");
        countVOList.add(countVO);
        return countVOList;
    }
}