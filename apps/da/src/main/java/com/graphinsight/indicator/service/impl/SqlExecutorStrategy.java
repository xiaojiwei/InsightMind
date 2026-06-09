package com.graphinsight.indicator.service.impl;

import com.graphinsight.indicator.enums.ExecutorPlatform;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;

@Service
public class SqlExecutorStrategy {

    @Resource(name = "dorisQueryExecutorService")
    private DorisQueryExecutorServiceImpl dorisQueryExecutorService;

    @Resource(name = "nullQueryExecutorService")
    private NullQueryExecutorServiceImpl nullQueryExecutorService;

    @Resource(name = "syncFileQueryExecutorService")
    private SyncFileQueryExecutorServiceImpl syncFileQueryExecutorService;

    public QueryExecutorService getSqlQueryMethod(ExecutorPlatform platform) {
        switch (platform) {
            case DORIS:
                return dorisQueryExecutorService;
            case SYNCFILE:
                return syncFileQueryExecutorService;
            default:
                return nullQueryExecutorService;
        }
    }

}
