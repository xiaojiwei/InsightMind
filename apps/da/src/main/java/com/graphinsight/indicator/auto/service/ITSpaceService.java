package com.graphinsight.indicator.auto.service;

import com.graphinsight.indicator.auto.entity.TSpace;
import com.baomidou.mybatisplus.extension.service.IService;

/**
 * <p>
 * 空间管理 服务类
 * </p>
 *
 * @author lixiaolong5
 * @since 2022-09-19
 */
public interface ITSpaceService extends IService<TSpace> {
    TSpace getAiSpaceById();
}
