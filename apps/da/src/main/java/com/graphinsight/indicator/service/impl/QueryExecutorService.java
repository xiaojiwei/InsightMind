package com.graphinsight.indicator.service.impl;

import com.graphinsight.indicator.enums.SourceType;
import com.graphinsight.indicator.model.BuildSqlTuple;
import com.graphinsight.indicator.model.QueryExecutorConfig;
import com.graphinsight.indicator.model.QueryResult;

/**
 */
public abstract class QueryExecutorService {

    public static final int RESULT_ROW_LIMIT = 10000;

    /**
     * 同步查询
     * @param tuple
     * @return
     */
    public abstract QueryResult query(BuildSqlTuple tuple);

    /**
     * 异步查询
     * @param tuple
     * @return
     */
    public abstract QueryResult queryAsync(BuildSqlTuple tuple);

    /**
     * 根据当前的执行引擎类型，替换sql中需要适配的字符、函数等
     * @param sourceType
     * @param tuple
     * @return
     */
    public String formatSqlByEngine(SourceType sourceType, BuildSqlTuple tuple) {

        String executeSql = formatSql(sourceType, tuple.getReviewSql());
        tuple.setExecuteSql(executeSql);

        return executeSql;

    }

    /**
     * 根据当前的执行引擎类型，替换sql中需要适配的字符、函数等
     * @param sourceType
     * @return
     */
    public static String formatSql(SourceType sourceType, String sql) {

        sql = sql.replaceAll(QueryExecutorConfig.QUOTE, QueryExecutorConfig.quoteMap.get(sourceType.toString()));
        sql = sql.replaceAll(QueryExecutorConfig.QUERY_ENGINE_PREFIX, QueryExecutorConfig.queryEnginePrefixMap.get(sourceType.toString()));

        //presto处理除数为零
        sql = sql.replaceAll(QueryExecutorConfig.EXP_DIVIDE_BY_ZERO_BEGIN, QueryExecutorConfig.divideByZeroBeginMap.get(sourceType.toString()));
        sql = sql.replaceAll(QueryExecutorConfig.EXP_DIVIDE_BY_ZERO_END, QueryExecutorConfig.divideByZeroEndMap.get(sourceType.toString()));

        //presto类型转换处理
        sql = sql.replaceAll(QueryExecutorConfig.EXP_CAST_COLUMN_BEGIN, QueryExecutorConfig.caseColumnBeginMap.get(sourceType.toString()));
        sql = sql.replaceAll(QueryExecutorConfig.EXP_CAST_COLUMN_END, QueryExecutorConfig.caseColumnEndMap.get(sourceType.toString()));

        sql = sql.replaceAll("DwDws\\.", "dw_dws\\.");
        sql = sql.replaceAll("DwDim\\.", "dw_dim\\.");
        sql = sql.replaceAll("DwDwd\\.", "dw_dwd\\.");
        sql = sql.replaceAll("DwAds\\.", "dw_ads\\.");

        sql = sql.replaceAll("BiDorisDim\\.", "dw_dim\\.");
        sql = sql.replaceAll("BiDorisDwd\\.", "dw_dwd\\.");
        sql = sql.replaceAll("BiDorisAds\\.", "dw_ads\\.");
        sql = sql.replaceAll("BiDoris\\.", "dw_dws\\.");

        return sql;

    }

    

}
