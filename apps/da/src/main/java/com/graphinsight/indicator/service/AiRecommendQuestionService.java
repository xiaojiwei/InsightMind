package com.graphinsight.indicator.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.IService;
import com.graphinsight.indicator.model.vo.AiRecommendQuestionPageParam;
import com.graphinsight.indicator.model.vo.AiRecommendQuestionVO;
import com.graphinsight.indicator.auto.entity.AiRecommendQuestionEntity;

/**
*
* @author fonlin
*/
public interface AiRecommendQuestionService extends IService<AiRecommendQuestionEntity> {

    /**
    * 根据条件分页
    *
    * @param pageParam 分页参数
    * @return  IPage
    */
    IPage<AiRecommendQuestionEntity> page(AiRecommendQuestionPageParam pageParam);

    /**
    * 更新
    *
    * @param aiRecommendQuestionVO    更新
    */
    void update(AiRecommendQuestionVO aiRecommendQuestionVO);

    /**
    * 新增
    *
    * @param aiRecommendQuestionVO    新增
    */
    void save(AiRecommendQuestionVO aiRecommendQuestionVO);

    /**
    * 根据 id 删除
    *
    * @param id    id
    */
    void delete(Long id);
}
