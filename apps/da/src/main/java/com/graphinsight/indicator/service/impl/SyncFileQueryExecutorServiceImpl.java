package com.graphinsight.indicator.service.impl;

import com.graphinsight.indicator.enums.SourceType;
import com.graphinsight.indicator.model.BuildSqlTuple;
import com.graphinsight.indicator.model.FileDownInfo;
import com.graphinsight.indicator.model.FileTask;
import com.graphinsight.indicator.model.QueryResult;
import com.graphinsight.indicator.service.RedisCacheService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import javax.sql.DataSource;
import java.sql.Connection;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Service("syncFileQueryExecutorService")
public class SyncFileQueryExecutorServiceImpl extends QueryExecutorService {

    /**
     *
     */
    ExecutorService fixedThreadPool = Executors.newFixedThreadPool(20);

    @Resource
    protected RedisCacheService redisCacheService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Override
    public QueryResult query(BuildSqlTuple tuple) {

        FileDownInfo fileDownInfo = new FileDownInfo();
        QueryResult queryResult = new QueryResult();
        //将sql模版替换为mysql sql语法，并设置executeSql，作为最终执行的sql。
        String sql = super.formatSqlByEngine(SourceType.MYSQL, tuple);
        String countSql = "select count(1) from (" + sql +") as T_CNT";
        fileDownInfo.setSql(sql);
        fileDownInfo.setCountSql(countSql);
        String downloadId = UUID.randomUUID().toString();
        fileDownInfo.setDownloadId(downloadId);
        fileDownInfo.setChoiceDimensionSet(tuple.getChoiceDimensionSet());
        fileDownInfo.setChoiceMeasureSet(tuple.getChoiceMeasureSet());
        fileDownInfo.setTuple(tuple);
        fileDownInfo.setMeasDetail(tuple.getQueryParam().isMeasureDetail());

        queryResult.setDownloadId(downloadId);
        redisCacheService.put(downloadId, fileDownInfo);

        FileTask fileTask = new FileTask();
        fileTask.setFileDownInfo(fileDownInfo);
        fileTask.setRedisCacheService(this.redisCacheService);
        DataSource dataSource = this.jdbcTemplate.getDataSource();
        try {
            Connection connection = dataSource.getConnection();
            fileTask.setConnection(connection);
        } catch (Exception ex) {
            ex.printStackTrace();
        }

        //异步下载文件
        this.queryAsync(fileTask);

        return queryResult;

    }

    private void queryAsync(FileTask fileTask) {

        fixedThreadPool.submit(fileTask);

    }

    @Override
    public QueryResult queryAsync(BuildSqlTuple tuple) {
        return null;
    }
}
