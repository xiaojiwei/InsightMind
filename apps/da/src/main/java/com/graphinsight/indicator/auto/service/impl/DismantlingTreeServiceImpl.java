package com.graphinsight.indicator.auto.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.graphinsight.indicator.auto.entity.DismantlingTree;
import com.graphinsight.indicator.auto.mapper.DismantlingTreeMapper;
import com.graphinsight.indicator.auto.service.IDismantlingTreeService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * <p>
 * 拆解树表 服务实现类
 * </p>
 *
 * @author lixiaolong5
 * @since 2022-11-22
 */
@Service
public class DismantlingTreeServiceImpl extends ServiceImpl<DismantlingTreeMapper, DismantlingTree> implements IDismantlingTreeService {

    @Override
    public List<DismantlingTree> getByMeasCode(String measCode) {
        QueryWrapper<DismantlingTree> queryWrapper = new QueryWrapper<>();
        queryWrapper.lambda().eq(DismantlingTree::getRootMeasCode, measCode);
        return baseMapper.selectList(queryWrapper);
    }

    @Override
    public Boolean hasSomeTree(long spaceId, String measCode) {
        QueryWrapper<DismantlingTree> queryWrapper = new QueryWrapper<>();
        queryWrapper.lambda().select(DismantlingTree::getId).eq(DismantlingTree::getRootMeasCode, measCode).eq(DismantlingTree::getSpaceId, spaceId).last("LIMIT 1");
        return baseMapper.selectOne(queryWrapper) != null;
    }
}
