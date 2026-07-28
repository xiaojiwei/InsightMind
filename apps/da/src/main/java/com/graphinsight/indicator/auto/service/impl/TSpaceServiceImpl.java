package com.graphinsight.indicator.auto.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import org.springframework.beans.factory.annotation.Value;
import com.graphinsight.indicator.auto.entity.TSpace;
import com.graphinsight.indicator.auto.mapper.TSpaceMapper;
import com.graphinsight.indicator.auto.service.ITSpaceService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.stereotype.Service;
import java.util.List;

/**
 * <p>
 * 空间管理 服务实现类
 * </p>
 *
 * @since 2022-09-19
 */
@Service
public class TSpaceServiceImpl extends ServiceImpl<TSpaceMapper, TSpace> implements ITSpaceService {

    @Value("${ai.space.id}")
    private Long id;

    @Override
    public TSpace getAiSpaceById() {
        if (id == null) {
            List<TSpace> list = getBaseMapper().selectList(Wrappers.<TSpace>lambdaQuery().orderByDesc(TSpace::getId));
            return list.isEmpty() ? null : list.get(0);
        }
        return this.getById(id);
    }
}
