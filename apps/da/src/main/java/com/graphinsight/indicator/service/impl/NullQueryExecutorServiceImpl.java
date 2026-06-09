package com.graphinsight.indicator.service.impl;

import com.graphinsight.indicator.model.BuildSqlTuple;
import com.graphinsight.indicator.model.QueryResult;
import org.springframework.stereotype.Service;

@Service("nullQueryExecutorService")
public class NullQueryExecutorServiceImpl extends QueryExecutorService {

    @Override
    public QueryResult query(BuildSqlTuple tuple) {
        return null;
    }

    @Override
    public QueryResult queryAsync(BuildSqlTuple tuple) {
        return null;
    }
}
