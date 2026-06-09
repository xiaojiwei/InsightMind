package com.graphinsight.indicator.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.IService;
import com.graphinsight.indicator.model.vo.AiBoardInfoPageParam;
import com.graphinsight.indicator.model.vo.AiBoardInfoVO;
import com.graphinsight.indicator.auto.entity.AiBoardInfo;
import com.graphinsight.indicator.model.vo.AiBusinessDelVo;

/**
*
* @author fonlin
*/
public interface AiBoardInfoService extends IService<AiBoardInfo> {

    /**
    * 根据条件分页
    *
    * @param pageParam 分页参数
    * @return  IPage
    */
    IPage<AiBoardInfoVO> page(AiBoardInfoPageParam pageParam);

    /**
    * 更新
    *
    * @param aiBoardInfoVO    更新
    */
    void update(AiBoardInfoVO aiBoardInfoVO);

    /**
    * 新增
    *
    * @param aiBoardInfoVO    新增
    */
    void save(AiBoardInfoVO aiBoardInfoVO);


    void delete(AiBusinessDelVo aiBoardDelVO);
}
