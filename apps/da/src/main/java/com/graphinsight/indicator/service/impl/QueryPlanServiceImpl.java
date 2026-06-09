package com.graphinsight.indicator.service.impl;

import com.baomidou.dynamic.datasource.annotation.DS;
import com.baomidou.dynamic.datasource.toolkit.DynamicDataSourceContextHolder;
import com.graphinsight.indicator.dao.QueryPlanDao;
import com.graphinsight.indicator.enums.JdbcDataSourceType;
import com.graphinsight.indicator.model.PageData;
import com.graphinsight.indicator.model.QueryPlan;
import com.graphinsight.indicator.service.QueryPlanService;
import com.graphinsight.indicator.service.RedisCacheService;
import com.graphinsight.indicator.service.SyncTaskService;
import com.graphinsight.indicator.util.UserThreadLocalUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.Date;

@DS("mysql")
@Service
public class QueryPlanServiceImpl implements QueryPlanService {

    @Autowired
    private QueryPlanDao queryPlanDao;

    @Resource
    protected RedisCacheService redisCacheService;

    @Autowired
    private SyncTaskService syncTaskService;

    public final static String DATA = "DATA_";

    private QueryPlan getQueryPlan(String key) {

        /*
        DynamicDataSourceContextHolder.push(JdbcDataSourceType.MYSQL.getDesc());
        QueryPlan queryPlan = this.queryPlanDao.findByKey(key);
        String userName = UserThreadLocalUtil.getUserName();

        Date now = new Date();
        if (null == queryPlan) {
            queryPlan = new QueryPlan();
            queryPlan.setKey(key);
            queryPlan.setCreator(userName);
            queryPlan.setCreateDate(now);
        }

        queryPlan.setUpdater(userName);
        queryPlan.setUpdateDate(now);
        return queryPlan;
*/

        return new QueryPlan();


    }

    public void supQueryPlan(String key, PageData pageData) {

//        QueryPlan queryPlan = this.getQueryPlan(key);
//        pageData.setQueryPlan(queryPlan);

    }

    @Override
    public void addCache(String key, PageData pageData, Long cost) {

//        this.addCntSumCost(key, cost);
//        this.addUpdate(key, cost);
//
        this.addCacheToRedis(key, pageData);

    }

    private void addCacheToRedis(String key, PageData pageData) {
        redisCacheService.put(DATA + key, pageData);
//        syncTaskService.syncAddCache(DATA + key, pageData);
    }

    @DS("mysql")
    public void addCacheCntSumCost(String key, Long cost) {

        /*
        QueryPlan queryPlan = this.getQueryPlan(key);
        Date now = new Date();

        Long queryCacheCnt = queryPlan.getQueryCacheCnt();
        Long queryCacheSumCost = queryPlan.getQueryCacheSumCost();
        Long sumCost = queryPlan.getSumCost();

        queryPlan.setQueryCacheCnt(++queryCacheCnt);
        queryPlan.setQueryCacheSumCost(queryCacheSumCost + cost);
        queryPlan.setSumCost(sumCost + cost);

        queryPlan.setLastQueryCacheCost(cost);
        queryPlan.setLastQueryCacheTime(now);

        this.save(queryPlan);

         */

    }

    public void addUpdate(String key, Long cost) {

        /*
        QueryPlan queryPlan = this.getQueryPlan(key);

        Date now = new Date();

        queryPlan.setLastUpdateCacheCost(cost);
        queryPlan.setLastUpdateQueryTime(now);

        this.save(queryPlan);
        */

    }

    public void addCntSumCost(String key, Long cost) {

        /*
        QueryPlan queryPlan = this.getQueryPlan(key);

        Date now = new Date();

        Long queryCnt = queryPlan.getQueryCnt();
        Long querySumCost = queryPlan.getQuerySumCost();
        Long sumCost = queryPlan.getSumCost();

        queryPlan.setQueryCnt(++queryCnt);
        queryPlan.setQuerySumCost(querySumCost + cost);
        queryPlan.setSumCost(sumCost + cost);

        queryPlan.setLastQueryTime(now);
        queryPlan.setLastQueryCost(cost);

        this.save(queryPlan);
         */

    }

    private void save(QueryPlan queryPlan) {
        try {
//            DynamicDataSourceContextHolder.push(JdbcDataSourceType.MYSQL.getDesc());
//            queryPlanDao.saveAndFlush(queryPlan);
        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }

    @Override
    public void delete(String key) {
//        redisCacheService.delete(DATA + key);
    }

    @Override
    public PageData getData(String key) {

        Long begin = System.currentTimeMillis();
        PageData pageData = redisCacheService.get(DATA + key, PageData.class);
        Long cost = System.currentTimeMillis() - begin;
        if (null != pageData) {
            pageData.setCacheKey(key);
        }

//        this.addCacheCntSumCost(key, cost);

        return pageData;

    }


}
