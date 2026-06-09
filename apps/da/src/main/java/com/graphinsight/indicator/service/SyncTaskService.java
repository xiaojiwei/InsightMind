package com.graphinsight.indicator.service;

import com.graphinsight.indicator.model.DataSource;
import com.graphinsight.indicator.model.DimensionQueryParam;
import com.graphinsight.indicator.model.PageData;

public interface SyncTaskService {

    void syncUpdate(DataSource queryDataSource);

    void syncUpdate(DimensionQueryParam dimQueryParam);

    void syncAddCache(String key, PageData pageData);

}
