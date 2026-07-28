package com.graphinsight.indicator.job;

import com.alibaba.fastjson.JSON;
import com.graphinsight.indicator.constant.IndicatorConstant;
import com.graphinsight.indicator.controller.SecretController;
import com.graphinsight.indicator.manager.FeiShuMsgManager;
import com.graphinsight.indicator.manager.HistogramManager;
import com.graphinsight.indicator.model.Response;
import com.graphinsight.indicator.model.dto.ColumnCheckResult;
import com.graphinsight.indicator.service.RedisCacheService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;

import javax.annotation.Resource;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * Date: 2022/3/5
 * Desc:
 */
@Component
@Slf4j
public class ColumnCheckJob {

    @Autowired
    private HistogramManager histogramManager;
    @Autowired
    RedisCacheService redisCacheService;
    @Resource
    SecretController secretController;
    @Resource
    FeiShuMsgManager feiShuMsgManager;
    @Value("${tableCheck:off}")
    private String tableCheck;

    @Scheduled(fixedRate = 1, timeUnit = TimeUnit.HOURS)
    public void execute() {
        try {
            if (Objects.equals(tableCheck,"on")){
                String value = UUID.randomUUID().toString();
                if (redisCacheService.tryLock(IndicatorConstant.COLUMN_CHECK_KEY, value, 30, TimeUnit.MINUTES)) {
                    log.info("列检查");
                    Response<List<ColumnCheckResult>> res = secretController.testTable();
                    if (res != null && !CollectionUtils.isEmpty(res.getData())) {
                        // 发送飞书消息
                        Map<String,String> contextMap = new HashMap<>();
                        contextMap.put("text",JSON.toJSONString(res.getData()));
                        feiShuMsgManager.sendTextMessageByEmail("zhangxinran@graphinsight.com", JSON.toJSONString(contextMap), "text", true);
                    }
                } else {
                    log.info("已有其他进程同步，本进程跳过处理");
                }
            }

        } catch (Exception e) {
            log.error("ColumnCheckJob 执行异常", e);
        }
    }
}
