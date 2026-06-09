package com.graphinsight.indicator.service.impl;

import com.graphinsight.indicator.model.BuildSqlTuple;
import com.graphinsight.indicator.model.PageData;
import com.graphinsight.indicator.service.DataQueryService;
import org.springframework.stereotype.Service;

@Service("sqlOnlyQuery")
public class SqlOnlyQueryServiceImpl extends DataQueryService {

    @Override
    public PageData queryData(BuildSqlTuple tuple, PageData pageData) {

        super.baseBuildSql(tuple, pageData);
        return pageData;

    }

}
