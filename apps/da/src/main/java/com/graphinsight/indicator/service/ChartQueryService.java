package com.graphinsight.indicator.service;

import com.graphinsight.indicator.model.*;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 单图（列表也是图的一种）ql执行
 */
public interface ChartQueryService {

    /**
     * 测试所有维度、指标是否可用
     */
    Map<String, String> test(String id);

    /**
     * 获取列轴上的所有数目
     * @param queryDataSource
     * @return
     */
    DataSource getColumnDataSource(DataSource queryDataSource);

    /**
     * 初始化日期维度
     * @param operator
     * @param filter
     * @param isTreeFilter
     */
    void initDayTimeRange(Operator operator, Filter filter, boolean isTreeFilter);

    /**
     * 获取行轴上的所有数目
     * @param dataSource
     * @return
     */
    DataSource getRowDataSource(DataSource dataSource);

    List<FilterTree> buildFilterTree(FilterTree dimFilter, List<Filter> dsFilters);

    FilterTree buildFilter(PageData pageData);

    PageData execQuery(DataSource queryDataSource);

    PageData execQuery(DataSource queryDataSource, boolean isSyncUpdate);

    PageData execCountQuery(DataSource queryDataSource);

    /**
     * 执行单个指标筛选的快捷方法
     * @param measureCode 目标指标code
     * @param filterList 过滤筛选条件code
     * @param dimSet 分组维度条件
     * @param username 用户唯一标识
     * @param spaceId 空间id
     * @return
     */
    PageData execMetaSingleMeasure(String measureCode, List<Filter> filterList, Set<String> dimSet, String username, Long spaceId);

    /**
     *
     * @param measureCode 目标指标code
     * @param dateCode 日期筛选code
     * @param dateInFilterParam 日期筛选时间
     * @param dimSet 分组code
     * @param username 用户唯一标识
     * @param spaceId 空间id
     * @return
     */
    String execOnlySingleMeasure(String measureCode, String dateCode, String dateInFilterParam, Set<String> dimSet, String username, Long spaceId);


    /**
     *
     * @param measureCode 目标指标code
     * @param dateCode 日期筛选code
     * @param dateInFilterParam 日期筛选时间
     * @param dimSet 分组code
     * @param username 用户唯一标识
     * @param spaceId 空间id
     * @return
     */
    PageData execMetaSingleMeasure(String measureCode, String dateCode, String dateInFilterParam, Set<String> dimSet, String username, Long spaceId);

    /**
     * 执行查询方法
     * @param md5Key
     * @param dataSource
     * @return
     */
    PageData query(String md5Key, DataSource dataSource);

    /**
     * 获取上下文数据
     * @param data
     * @param username
     * @return
     */
    List<String> applyAuthContextDataList(String data, String username);

    /**
     * 获取空间下人员的所有授权。
     * @param spaceId
     * @param userName
     * @return
     */
    Set<AuthElement> getAuthElementSet(Long spaceId, String userName);

    PageData testQuery(Long id);

    /**
     * 增加查询日志
     * @param dataSource
     */
    void addQueryLog(DataSource dataSource, PageData pageData);

    /**
     * 构建维度筛选key
     * @param dataSource
     * @return
     */
    String buildAuthCacheKeyInfo(DataSource dataSource);

    /**
     * 获得jdbc
     * @return
     */
    JdbcTemplate getJdbcTemplate();

    /**
     * 获得redis
     * @return
     */
    RedisCacheService getRedisCacheService();

}
