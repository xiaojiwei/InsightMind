package com.graphinsight.indicator.auto.service.impl;

import com.graphinsight.indicator.auto.entity.AuditLog;
import com.graphinsight.indicator.auto.mapper.AuditLogMapper;
import com.graphinsight.indicator.auto.service.IAuditLogService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.stereotype.Service;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author lixiaolong5
 * @since 2022-08-23
 */
@Service
public class AuditLogServiceImpl extends ServiceImpl<AuditLogMapper, AuditLog> implements IAuditLogService {

}
