package com.graphinsight.indicator.service.impl;

import com.graphinsight.indicator.enums.ExecutorPlatform;
import com.graphinsight.indicator.model.*;
import com.graphinsight.indicator.service.DataQueryService;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;

import java.util.List;

@Service("countDataQuery")
public class CountDataQueryServiceImpl extends DataQueryService {

    @Override
    public PageData queryData(BuildSqlTuple tuple, PageData pageData) {
        return this.queryCountQuery(tuple, pageData);
    }

    public PageData queryCountQuery(BuildSqlTuple tuple, PageData pageData) {

        QueryParam queryParam = tuple.getQueryParam();
        String queryCntKeyId = queryParam.getQueryCountId();

        CountInfo countInfo = redisCacheService.get(queryCntKeyId, CountInfo.class);

        Integer pageSize = countInfo.getPageSize();
        Integer pageNo = countInfo.getPageNo();

        ExecutorPlatform platform = countInfo.getPlatform();

        String countSql = countInfo.getCntSql();
        tuple.setPlatform(platform);
        tuple.setReviewSql(countSql);
        tuple.setCountSql(true);
        tuple.setDbName(countInfo.getDbName());
        tuple.setDbType(countInfo.getDbType());
        tuple.getQueryParam().setSourceType(countInfo.getSourceType());
        tuple.setUserName(countInfo.getUserName());

        pageData.setReviewSql(countSql);

        QueryResult queryResult = this.queryData(tuple);
        Integer cnt = this.getCount(queryResult);
        countInfo.setCount(cnt);

        redisCacheService.put(queryCntKeyId, countInfo);

        PageInfo pageInfo = new PageInfo(pageSize);
        pageInfo.setTotalRows(cnt);
        pageInfo.calc();
        pageInfo.calcRange(pageNo);
        pageData.setPageInfo(pageInfo);

        return pageData;
    }

    public static Integer getCount(QueryResult queryResult) {

        Integer cnt = 0;
        List<List<String>> valueList = queryResult.getValues();
        if (!CollectionUtils.isEmpty(valueList) && valueList.size() > 0) {

            List<String> rowList = valueList.get(0);
            if (!CollectionUtils.isEmpty(rowList) && rowList.size() > 0) {

                String value = rowList.get(0);
                cnt = Integer.valueOf(value);
            }
        }

        return cnt;

    }
}
