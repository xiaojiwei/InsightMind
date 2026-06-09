package com.graphinsight.indicator.service.impl;

import com.graphinsight.indicator.model.DataSource;
import com.graphinsight.indicator.model.DimensionQueryParam;
import com.graphinsight.indicator.model.PageData;
import com.graphinsight.indicator.service.ChartQueryService;
import com.graphinsight.indicator.service.DimensionQueryService;
import com.graphinsight.indicator.service.RedisCacheService;
import com.graphinsight.indicator.service.SyncTaskService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;

@Service
public class SyncTaskServiceImpl implements SyncTaskService {

    @Lazy
    @Autowired
    private ChartQueryService chartQueryService;

    @Lazy
    @Autowired
    private DimensionQueryService dimensionQueryService;

    @Lazy
    @Resource
    protected RedisCacheService redisCacheService;

    @Override
    @Async("taskExecutor")
    public void syncUpdate(DataSource queryDataSource) {
        chartQueryService.execQuery(queryDataSource, true);
    }

    @Override
    @Async("taskExecutor")
    public void syncUpdate(DimensionQueryParam dimQueryParam) {
        dimensionQueryService.execQueryDimensionValues(dimQueryParam, true);
    }

    @Override
    @Async("taskExecutor")
    public void syncAddCache(String key, PageData pageData) {
        redisCacheService.put(key, pageData);
    }

}
