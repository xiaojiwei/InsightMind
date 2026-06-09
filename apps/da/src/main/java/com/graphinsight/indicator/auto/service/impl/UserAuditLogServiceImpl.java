package com.graphinsight.indicator.auto.service.impl;

import com.graphinsight.indicator.auto.entity.UserAuditLog;
import com.graphinsight.indicator.auto.mapper.UserAuditLogMapper;
import com.graphinsight.indicator.auto.service.IUserAuditLogService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.stereotype.Service;

/**
 * <p>
 * 用户操作记录表 服务实现类
 * </p>
 *
 * @author lixiaolong5
 * @since 2023-02-09
 */
@Service
public class UserAuditLogServiceImpl extends ServiceImpl<UserAuditLogMapper, UserAuditLog> implements IUserAuditLogService {

}
