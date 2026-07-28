package com.graphinsight.indicator.manager;

import com.graphinsight.indicator.auto.entity.AuditLog;
import com.graphinsight.indicator.auto.service.IAuditLogService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.util.concurrent.CompletableFuture;

/**
 * Date: 2022/8/23
 * Desc:
 */
@Slf4j
@Component
public class AuditLogManager {

    @Resource
    IAuditLogService auditLogService;

    public void asyncSave(AuditLog auditLog){
        if (auditLog != null){
            CompletableFuture.runAsync(() -> {
                auditLogService.save(auditLog);
            });
        }
    }
}
