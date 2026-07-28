package com.graphinsight.indicator.interceptor;

import com.alibaba.fastjson.JSON;
import com.baomidou.mybatisplus.extension.plugins.inner.InnerInterceptor;
import com.graphinsight.indicator.auto.entity.AuditLog;
import com.graphinsight.indicator.auto.service.IAuditLogService;
import com.graphinsight.indicator.util.UserThreadLocalUtil;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.apache.ibatis.executor.Executor;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.skywalking.apm.toolkit.trace.TraceContext;
import org.springframework.context.annotation.Lazy;
import org.springframework.util.StringUtils;

import javax.annotation.Resource;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.concurrent.CompletableFuture;

/**
 * Date: 2022/8/23
 * Desc:
 */
@Slf4j
@Data
public class MybatisplusOperateInterceptor implements InnerInterceptor {

    @Lazy
    @Resource
    IAuditLogService auditLogService;

    @Override
    public void beforeUpdate(Executor executor, MappedStatement ms, Object parameter) throws SQLException {

        String id = ms.getId();
        if (StringUtils.hasLength(id) && (!id.contains("AuditLogMapper") && !id.contains("AuditLogService"))
                && !"anonymous".equalsIgnoreCase(UserThreadLocalUtil.getUserName())){
            try {
                asyncSave(createAudit(ms,parameter));
            } catch (Exception e) {
                log.error("审计异常:",e);
            }
        }
    }

    private AuditLog createAudit(MappedStatement ms, Object parameter){
        AuditLog auditLog = new AuditLog();
        String traceId = TraceContext.traceId();
        String sql = ms.getBoundSql(parameter).getSql();
        auditLog.setSql(sql);
        auditLog.setUsername(UserThreadLocalUtil.getUserName());
        auditLog.setParam(JSON.toJSONString(parameter));
        auditLog.setOperateType(ms.getSqlCommandType().name());
        auditLog.setOperateTime(LocalDateTime.now());
        auditLog.setTraceId(traceId);
        return auditLog;
    }



    public void asyncSave(AuditLog auditLog){
        if (auditLog != null){
            CompletableFuture.runAsync(() -> {
                auditLogService.save(auditLog);
            });
        }
    }


}
