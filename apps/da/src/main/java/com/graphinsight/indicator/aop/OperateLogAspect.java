package com.graphinsight.indicator.aop;

import com.alibaba.fastjson.JSON;
import com.graphinsight.indicator.annotation.OperateLog;
import com.graphinsight.indicator.auto.entity.UserAuditLog;
import com.graphinsight.indicator.auto.service.IUserAuditLogService;
import com.graphinsight.indicator.util.UserThreadLocalUtil;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * @Description: 日志切面
 * @Date: 2021/11/16
 */
@Component
@Aspect
@Slf4j
public class OperateLogAspect {

    @Resource
    IUserAuditLogService userAuditLogService;

    @Before("@annotation(operateLog)")
    public void webLog(JoinPoint point, OperateLog operateLog){
        String methodName = point.getSignature().toShortString();
        try {
            String skywalkingTraceId = UUID.randomUUID().toString();
            HttpServletRequest request = ((ServletRequestAttributes) RequestContextHolder.getRequestAttributes()).getRequest();
            String ipAddr = getRemoteHost(request);
            String url = request.getRequestURL().toString();
            Object[] args = point.getArgs();
            String param = JSON.toJSONString(args);
            UserAuditLog userAuditLog = new UserAuditLog();
            userAuditLog.setTime(LocalDateTime.now());
            userAuditLog.setUsername(UserThreadLocalUtil.getUserName());
            userAuditLog.setParam(param);
            userAuditLog.setIp(ipAddr);
            userAuditLog.setTraceId(skywalkingTraceId);
            userAuditLog.setMethod(methodName);
            userAuditLog.setUrl(url);
            saveLog(userAuditLog);
            log.info("IP: {},请求URL :{}, 方法：{} , params : {}",ipAddr,url, methodName, param);
        } catch (Throwable throwable) {
            log.error("method: [{}] write log error,user:[{}] exception : ", methodName, UserThreadLocalUtil.getUserName(), throwable);
        }
    }

    @Async
    public void saveLog(UserAuditLog log){
        userAuditLogService.save(log);
    }


    /**
     * 获取目标主机的ip
     * @param request
     * @return
     */
    private String getRemoteHost(HttpServletRequest request) {
        String ip = request.getHeader("x-forwarded-for");
        if (ip == null || ip.length() == 0 || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("Proxy-Client-IP");
        }
        if (ip == null || ip.length() == 0 || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("WL-Proxy-Client-IP");
        }
        if (ip == null || ip.length() == 0 || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getRemoteAddr();
        }
        return "0:0:0:0:0:0:0:1".equals(ip) ? "127.0.0.1" : ip;
    }

}
