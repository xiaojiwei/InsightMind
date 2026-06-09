package com.graphinsight.indicator.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import cn.hutool.core.bean.BeanUtil;
import com.graphinsight.indicator.service.AiRecommendQuestionService;
import com.graphinsight.indicator.model.vo.AiRecommendQuestionPageParam;
import com.graphinsight.indicator.model.vo.AiRecommendQuestionVO;
import com.graphinsight.indicator.auto.entity.AiRecommendQuestionEntity;
import com.graphinsight.indicator.auto.mapper.AiRecommendQuestionMapper;
import org.springframework.stereotype.Service;

/**
* @author houfenglei
*/
@Service
public class AiRecommendQuestionServiceImpl extends ServiceImpl<AiRecommendQuestionMapper, AiRecommendQuestionEntity> implements AiRecommendQuestionService {

    @Override
    public IPage<AiRecommendQuestionEntity> page(AiRecommendQuestionPageParam pageParam) {
        LambdaQueryWrapper<AiRecommendQuestionEntity> queryWrapper = Wrappers.lambdaQuery(AiRecommendQuestionEntity.class);
        return super.page(new Page<>(pageParam.getCurrentPage(), pageParam.getPageSize()), queryWrapper);
    }

    @Override
    public void update(AiRecommendQuestionVO VO) {
        AiRecommendQuestionEntity toUpdate = BeanUtil.copyProperties(VO, AiRecommendQuestionEntity.class);
        super.updateById(toUpdate);
    }

    @Override
    public void save(AiRecommendQuestionVO VO) {
        AiRecommendQuestionEntity toSave = BeanUtil.copyProperties(VO, AiRecommendQuestionEntity.class);
        super.save(toSave);
    }
    
    @Override
    public void delete(Long id) {
        super.removeById(id);
    }
}