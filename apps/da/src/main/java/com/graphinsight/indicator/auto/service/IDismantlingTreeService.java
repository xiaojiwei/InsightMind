package com.graphinsight.indicator.auto.service;

import com.graphinsight.indicator.auto.entity.DismantlingTree;
import com.baomidou.mybatisplus.extension.service.IService;

import java.util.List;

/**
 * <p>
 * 拆解树表 服务类
 * </p>
 *
 * @author lixiaolong5
 * @since 2022-11-22
 */
public interface IDismantlingTreeService extends IService<DismantlingTree> {
    List<DismantlingTree> getByMeasCode(String measCode);

    Boolean hasSomeTree(long spaceId, String measCode);
}
