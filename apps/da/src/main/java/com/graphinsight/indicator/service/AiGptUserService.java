package com.graphinsight.indicator.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.IService;
import com.graphinsight.indicator.model.vo.AiGptLimitVo;
import com.graphinsight.indicator.model.vo.AiGptUserPageParam;
import com.graphinsight.indicator.model.vo.AiGptUserVO;
import com.graphinsight.indicator.entity.AiGptUser;
import com.graphinsight.indicator.util.UserThreadLocalUtil;

import java.util.Set;

/**
*
* @author fonlin
*/
public interface AiGptUserService extends IService<AiGptUser> {

    /**
    * 根据条件分页
    *
    * @param pageParam 分页参数
    * @return  IPage
    */
    IPage<AiGptUser> page(AiGptUserPageParam pageParam);

    /**
    * 更新
    *
    * @param aiGptUserVO    更新
    */
    void update(AiGptUserVO aiGptUserVO);

    /**
    * 新增
    *
    * @param aiGptUserVO    新增
    */
    void save(AiGptUserVO aiGptUserVO);

    /**
    * 根据 id 删除
    *
    * @param id    id
    */
    void delete(Long id);

    AiGptLimitVo limitUser();

}
