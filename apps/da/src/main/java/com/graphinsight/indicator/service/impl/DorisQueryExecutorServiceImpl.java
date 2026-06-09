package com.graphinsight.indicator.service.impl;

import com.graphinsight.indicator.model.DataConnection;
import com.alibaba.fastjson.JSON;
import com.graphinsight.indicator.enums.CacheStrategy;
import com.graphinsight.indicator.enums.SourceType;
import com.graphinsight.indicator.model.BuildSqlTuple;
import com.graphinsight.indicator.model.Measure;
import com.graphinsight.indicator.model.PageData;
import com.graphinsight.indicator.model.QueryResult;
import com.graphinsight.indicator.service.QueryPlanService;
import com.graphinsight.indicator.service.RedisCacheService;
import org.mortbay.log.Log;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.support.rowset.SqlRowSet;
import org.springframework.jdbc.support.rowset.SqlRowSetMetaData;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;

import javax.annotation.Resource;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Service("dorisQueryExecutorService")
public class DorisQueryExecutorServiceImpl extends QueryExecutorService {


    @Autowired
    @Qualifier("secondJdbcTemplate")
    private JdbcTemplate defaultJdbcTemplate;

    @Autowired
    @Qualifier("dipJdbcTemplate")
    private JdbcTemplate dipJdbcTemplate;

    @Autowired
    private RedisTemplate redisTemplate;

    @Resource
    protected RedisCacheService redisCacheService;

    @Override
    public QueryResult query(BuildSqlTuple tuple) {

        QueryResult queryResult = new QueryResult();
        //将sql模版替换为mysql sql语法，并设置executeSql，作为最终执行的sql。
        String executeSql = super.formatSqlByEngine(SourceType.MYSQL, tuple);
        Log.info(executeSql);
        JdbcTemplate jdbcTemplate = getJdbc(tuple);
        boolean measureDetail = tuple.isMeasureDetail();
        if (measureDetail) {

            SqlRowSet sqlRowSet = jdbcTemplate.queryForRowSet(executeSql);
            SqlRowSetMetaData metaData = sqlRowSet.getMetaData();
            String[] columnNames = metaData.getColumnNames();
            queryResult.setColumnNames(columnNames);
            for (int i = 0; i < columnNames.length; i++) {
                int idx = i + 1;
                String colName = metaData.getColumnLabel(idx);
                String colType = metaData.getColumnTypeName(idx);
                queryResult.getColTypeMap().put(colName, colType);
            }

        }

        CacheStrategy cacheStrategy = CacheStrategy.OVERWRITE;
        if (null != tuple.getQueryParam() && null != tuple.getQueryParam().getDataSource() && null != tuple.getQueryParam().getDataSource().getCacheStrategy()) {
            cacheStrategy = tuple.getQueryParam().getDataSource().getCacheStrategy();
        }

        final Long timeout = Long.valueOf(24 * 60 * 60 * 1000);
        if (CacheStrategy.OVERWRITE.equals(cacheStrategy)) {

            final List queryList = jdbcTemplate.queryForList(executeSql);
            CompletableFuture.runAsync(() -> {
//                redisTemplate.boundValueOps(executeSql).set(JSON.toJSONString(queryList), timeout, TimeUnit.MILLISECONDS);
                redisCacheService.put(executeSql, queryList, timeout, TimeUnit.MILLISECONDS);
            });

            queryResult.setValueMap(queryList);

        } else {

            List<Map<String, Object>> list = redisCacheService.get(executeSql, List.class);
            if (CollectionUtils.isEmpty(list)) {
                final List queryList = jdbcTemplate.queryForList(executeSql);
                list = queryList;
                CompletableFuture.runAsync(() -> {
//                    redisTemplate.boundValueOps(executeSql).set(JSON.toJSONString(queryList), timeout, TimeUnit.MILLISECONDS);
                    redisCacheService.put(executeSql, queryList, timeout, TimeUnit.MILLISECONDS);
                });
            } else {
                CompletableFuture.runAsync(() -> {
                    List<Map<String, Object>> asyList = jdbcTemplate.queryForList(executeSql);
//                    redisTemplate.boundValueOps(executeSql).set(JSON.toJSONString(asyList), timeout, TimeUnit.MILLISECONDS);
                    redisCacheService.put(executeSql, asyList, timeout, TimeUnit.MILLISECONDS);

                });
            }

            queryResult.setValueMap(list);

        }

        return queryResult;

    }

    /** Cache of dynamically created JdbcTemplates, keyed by DataConnection.cacheKey(). */
    private final ConcurrentHashMap<String, JdbcTemplate> dynamicJdbcCache = new ConcurrentHashMap<>();

    private JdbcTemplate getJdbc(BuildSqlTuple tuple) {
        DataConnection conn = tuple.getConnection();
        if (conn == null) {
            return defaultJdbcTemplate;
        }
        return dynamicJdbcCache.computeIfAbsent(conn.cacheKey(), k -> {
            DriverManagerDataSource ds = new DriverManagerDataSource();
            ds.setDriverClassName(conn.driverClassName());
            ds.setUrl(conn.buildJdbcUrl());
            ds.setUsername(conn.getDbUser());
            ds.setPassword(conn.getDbPassword());
            Properties props = new Properties();
            props.setProperty("sessionVariables",
                    "sql_mode='STRICT_TRANS_TABLES,ERROR_FOR_DIVISION_BY_ZERO,NO_ENGINE_SUBSTITUTION'");
            ds.setConnectionProperties(props);
            return new JdbcTemplate(ds);
        });
    }

    @Override
    public QueryResult queryAsync(BuildSqlTuple tuple) {
        return null;
    }
}
