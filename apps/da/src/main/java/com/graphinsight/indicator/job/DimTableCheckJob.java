package com.graphinsight.indicator.job;

import com.alibaba.fastjson.JSON;
import com.graphinsight.indicator.constant.IndicatorConstant;
import com.graphinsight.indicator.manager.DimensionManager;
import com.graphinsight.indicator.manager.FeiShuMsgManager;
import com.graphinsight.indicator.manager.HistogramManager;
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
 * Author: lixiaolong
 * Date: 2022/3/5
 * Desc:
 */
@Component
@Slf4j
public class DimTableCheckJob {

    @Autowired
    private HistogramManager histogramManager;
    @Autowired
    RedisCacheService redisCacheService;
    @Resource
    DimensionManager dimensionManager;
    @Resource
    FeiShuMsgManager feiShuMsgManager;
    @Value("${tableCheck:off}")
    private String tableCheck;

    @Scheduled(fixedRate = 10, timeUnit = TimeUnit.MINUTES)
    public void execute() {
        try {
            if (Objects.equals(tableCheck,"on")){
                String value = UUID.randomUUID().toString();
                if (redisCacheService.tryLock(IndicatorConstant.DIMTABLE_CHECK_KEY, value, 5, TimeUnit.MINUTES)) {
                    log.info("维表检查");
                    List<ColumnCheckResult> res = dimensionManager.checkDimensionTable();
                    if (!CollectionUtils.isEmpty(res)) {
                        // 发送飞书消息
                        Map<String,String> contextMap = new HashMap<>();
                        contextMap.put("text",JSON.toJSONString(res));
                        feiShuMsgManager.sendTextMessageByEmail("lixiaolong5@graphinsight.com", JSON.toJSONString(contextMap), "text", true);
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
