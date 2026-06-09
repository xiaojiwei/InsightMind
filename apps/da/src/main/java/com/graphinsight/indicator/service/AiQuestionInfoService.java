package com.graphinsight.indicator.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.IService;
import com.graphinsight.indicator.model.vo.AiQuestionCountVO;
import com.graphinsight.indicator.model.vo.AiQuestionInfoPageParam;
import com.graphinsight.indicator.model.vo.AiQuestionInfoVO;
import com.graphinsight.indicator.auto.entity.AiQuestionInfo;

import java.util.List;

/**
*
* @author fonlin
*/
public interface AiQuestionInfoService extends IService<AiQuestionInfo> {

    /**
    * 根据条件分页
    *
    * @param pageParam 分页参数
    * @return  IPage
    */
    IPage<AiQuestionInfoVO> page(AiQuestionInfoPageParam pageParam);



    /**
    * 更新
    *
    * @param aiQuestionInfoVO    更新
    */
    void update(AiQuestionInfoVO aiQuestionInfoVO);

    /**
    * 新增
    *
    * @param aiQuestionInfoVO    新增
    */
    void save(AiQuestionInfoVO aiQuestionInfoVO);

    /**
    * 根据 id 删除
    *
    * @param id    id
    */
    void delete(Long id);

    List<AiQuestionCountVO> getCountInfo(AiQuestionInfoPageParam aiBusinessSearchVo);
}
