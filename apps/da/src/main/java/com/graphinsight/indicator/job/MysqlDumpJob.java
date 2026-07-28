package com.graphinsight.indicator.job;

import com.graphinsight.indicator.manager.MysqlDumpManager;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Date: 2022/3/5
 * Desc:
 */
@Component
@Slf4j
public class MysqlDumpJob {

    @Value("${mysqlDumpSwitch:off}")
    private String mysqlDumpSwitch;

    @Autowired
    private MysqlDumpManager mysqlDumpManager;


    @Scheduled(cron = "0 0 3 * * ?")
    public void execute() {
        if ("on".equalsIgnoreCase(mysqlDumpSwitch)){
            try {
                mysqlDumpManager.dump();
            } catch (Exception e) {
                log.error("备份库异常:",e);
            }
        }
    }
}
