package com.graphinsight.indicator.auto.service.impl;

import com.graphinsight.indicator.auto.entity.AuthElement;
import com.graphinsight.indicator.auto.mapper.AuthElementMapper;
import com.graphinsight.indicator.auto.service.IAuthElementService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.stereotype.Service;

/**
 * <p>
 * 属权元素指标或维度 服务实现类
 * </p>
 *
 * @since 2023-02-13
 */
@Service
public class AuthElementServiceImpl extends ServiceImpl<AuthElementMapper, AuthElement> implements IAuthElementService {

}
