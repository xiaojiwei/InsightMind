package com.graphinsight.indicator.service;

import com.graphinsight.indicator.model.PageData;

/**
 * 缓存服务接口
 */
public interface QueryPlanService {

    /**
     * 增加查询cnt
     */
    void addCache(String key, PageData pageData, Long cost);

    /**
     * 清空cache
     * @param key
     */
    void delete(String key);

    /**
     * 获取数据
     * @param key
     * @return
     */
    PageData getData(String key);

    /**
     * 增加查询总次数、总耗时
     * @param key
     * @param cost
     */
    void addCntSumCost(String key, Long cost);

    /**
     * 增加缓存查询总次数、总耗时
     * @param key
     * @param cost
     */
    void addCacheCntSumCost(String key, Long cost);

    /**
     * 补充查询统计信息到返回结果
     * @param key
     * @param pageData
     */
    void supQueryPlan(String key, PageData pageData);

}
