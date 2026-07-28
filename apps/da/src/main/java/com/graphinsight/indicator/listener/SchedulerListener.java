package com.graphinsight.indicator.listener;

import com.graphinsight.indicator.constant.IndicatorConstant;
import com.graphinsight.indicator.manager.MeasureMonitorManager;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.web.context.WebServerInitializedEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.util.Objects;

/**
 * Date: 2022/10/9
 * Desc: 任务调度启动类
 */
@Slf4j
@Component
public class SchedulerListener implements ApplicationListener<WebServerInitializedEvent> {

    @Resource
    MeasureMonitorManager measureMonitorManager;

    @Override
    public void onApplicationEvent(WebServerInitializedEvent event) {
        Environment environment = event.getApplicationContext().getEnvironment();
        // 检查是否启动任务调度配置
        String enable = environment.getProperty(IndicatorConstant.SCHEDULER_CONFIG_KEY);
        if (Objects.equals(enable,"1")){
            // 调度任务生效
            log.info("调度功能开启");
            IndicatorConstant.MEASURE_MONITOR_ENABLE = true;
            measureMonitorManager.registJob();
        } else {
            // 不做任何动作
        }
    }
}
