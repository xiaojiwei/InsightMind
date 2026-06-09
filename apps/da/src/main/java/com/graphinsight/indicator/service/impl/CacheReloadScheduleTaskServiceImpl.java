package com.graphinsight.indicator.service.impl;


import com.baomidou.dynamic.datasource.toolkit.DynamicDataSourceContextHolder;
import com.graphinsight.indicator.dao.CacheReloadTaskDao;
import com.graphinsight.indicator.dao.DimAllValuesDao;
import com.graphinsight.indicator.dao.QueryPlanDao;
import com.graphinsight.indicator.enums.*;
import com.graphinsight.indicator.model.*;
import com.graphinsight.indicator.service.*;
import lombok.extern.slf4j.Slf4j;
import org.ansj.domain.Result;
import org.ansj.domain.Term;
import org.ansj.splitWord.analysis.DicAnalysis;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.util.CollectionUtils;

import javax.annotation.Resource;
import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;
import javax.persistence.Query;
import java.time.LocalDateTime;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;

@Configuration
@EnableScheduling
@Slf4j
public class
CacheReloadScheduleTaskServiceImpl implements CacheReloadScheduleTaskService {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Lazy
    @Autowired
    private ChartQueryService chartQueryService;

    @Lazy
    @Autowired
    private DimensionQueryService dimQueryService;

    @Lazy
    @Autowired
    private CacheReloadTaskDao cacheReloadTaskDao;

    @Autowired
    private DimAllValuesDao dimAllValuesDao;

    @Lazy
    @Resource
    protected RedisCacheService redisCacheService;

    @Lazy
    @Autowired
    private QueryPlanDao queryPlanDao;

    @Lazy
    @Autowired
    private IndicatorService indicatorService;

    @PersistenceContext
    private EntityManager entityManager;

    //3.添加定时任务
//    @Scheduled(cron = "0/5 * * * * ?")
//    @Scheduled(cron = "0 0 1 * * ?")
    public void createCacheJobTask() {
        //复制任务task
        this.createCacheTask();

    }

//    @Scheduled(cron = "0/5 * * * * ?")
//    @Scheduled(cron = "0 0 4 * * ?")
    public void flushCacheDataJobTask() {
        //刷新数据
        this.flushCacheData();
    }

//    @Scheduled(cron = "0/5 * * * * ?")
//    @Scheduled(cron = "0 0 2 * * ?")
    public void flushAllDimDataJobTask() {
        //构建所有维度的数据值
        this.buildAllDimData();
    }

//    @Scheduled(cron = "0 0 3 * * ?")
    public void buildCertainDimDataJobTask() {
        //构建特定维度的数据值
        Set<String> dimNames = new HashSet<>();
        dimNames.add("省份名称");
        dimNames.add("城市名称");
        dimNames.add("车型车系名称");
        this.buildDimData(dimNames);
    }

    private boolean skeep(Dimension dim) {

        String name = dim.getName();
        if (name.indexOf("时") >= 0) {
            return true;
        }

        if (name.indexOf("年") >= 0) {
            return true;
        }

        if (name.indexOf("月") >= 0) {
            return true;
        }

        if (name.indexOf("日") >= 0) {
            return true;
        }

        if (name.indexOf("描述") >= 0) {
            return true;
        }

        return false;

    }

    @Async
    @Override
    public void buildDimData(Set<String> dimNames){
        List<Dimension> allDimList = indicatorService.listAllDimension();
        for (Dimension dim : allDimList){
            try {
                if (dimNames.contains(dim.getName())){
                    String hql = "select dav From DimAllValues as dav where dav.dimCode = " + "'" + dim.getCode() + "'";
                    Query query = this.entityManager.createQuery(hql);
                    List list = query.getResultList();
                    if (null != list && list.size() == 0) {
                        log.info("开始往维值表插入数据，dimName:{},dimCode:{}",dim.getName(),dim.getCode());
                        DimensionQueryParam dimQueryParam = new DimensionQueryParam();
                        dimQueryParam.setCacheStrategy(CacheStrategy.DEFAULT);
                        dimQueryParam.setCode(dim.getCode());
                        dimQueryParam.setCacheTable(false);
                        dimQueryParam.setPageNo(0);
                        dimQueryParam.setPageSize(20000);
                        PageData pageData = new PageData();
                        pageData = this.dimQueryService.execQueryDimensionValues(dimQueryParam, false);
                        String dimCode = dim.getCode();
                        String dimName = dim.getName();
                        for (List<Cell> cells : pageData.getCellList()) {
                            for (Cell cell : cells) {

                                String key = cell.getId();
                                String text = cell.getData();

                                DimAllValues dimAllValues = new DimAllValues();
                                dimAllValues.setDimCode(dimCode);
                                dimAllValues.setDimName(dimName);
                                dimAllValues.setValueKey(key);

                                dimAllValues.setValueText(text);



                                this.dimAllValuesDao.saveAndFlush(dimAllValues);

                            }
                        }
                        log.info("往维值表插入数据完成，dimName:{},dimCode:{}",dim.getName(),dim.getCode());
                        sleep();
                    }
                }
            }catch (Exception e){
                log.error("往维值表插入数据失败，dimName:{},dimCode:{}",dim.getName(),dim.getCode(),e);
            }
        }
    }

    /**
     * 构建所有维度的维值
     */
    @Async
    public void buildAllDimData() {

        log.info("构建所有维度维值任务开始运行");

        this.dimAllValuesDao.truncateAll();

        List<Dimension> allDimList = indicatorService.listAllDimension();

        for (Dimension dim : allDimList) {

            try {

                if (skeep(dim)) {
                    continue;
                }

                ViewType viewType = dim.getViewType();
                if (ViewType.DAY.equals(viewType)
                        || ViewType.WEEK.equals(viewType)
                        || ViewType.MONTH.equals(viewType)
                        || ViewType.SEASON.equals(viewType)
                        || ViewType.YEAR.equals(viewType)) {
                    continue;
                }

                DimensionQueryParam dimQueryParam = new DimensionQueryParam();
                dimQueryParam.setCacheStrategy(CacheStrategy.DEFAULT);
                dimQueryParam.setCode(dim.getCode());
                dimQueryParam.setCacheTable(false);
                dimQueryParam.setPageNo(0);
                dimQueryParam.setPageSize(20000);
                PageData pageData = new PageData();

                pageData = this.dimQueryService.execQueryDimensionValues(dimQueryParam, false);

                if (DimType.DEGENERATE_DIM.equals(dim.getDimType()) && pageData.getCellList().size() > 200) {
                    continue;
                }

                String dimCode = dim.getCode();
                String dimName = dim.getName();
                for (List<Cell> cells : pageData.getCellList()) {
                    for (Cell cell : cells) {

                        String key = cell.getId();
                        String text = cell.getData();

                        DimAllValues dimAllValues = new DimAllValues();
                        dimAllValues.setDimCode(dimCode);
                        dimAllValues.setDimName(dimName);
                        dimAllValues.setValueKey(key);
                        if (null == text || text.length() > 12 || text.length() < 2) {
                            continue;
                        }
                        dimAllValues.setValueText(text);

                        Result result = DicAnalysis.parse(text);
                        List<Term> termList = result.getTerms();

                        String nature = this.getNature(termList);
                        if ("sentence".equalsIgnoreCase(nature)
                                || "v".equalsIgnoreCase(nature)
                                || "vn".equalsIgnoreCase(nature)
                                || "r".equalsIgnoreCase(nature)) {
                            continue;
                        }
                        dimAllValues.setNature(nature);
                        this.dimAllValuesDao.saveAndFlush(dimAllValues);

                    }
                }

                sleep();

            } catch (Exception ex) {
                log.error("构建维度维值失败，dim:{}",dim,ex);
                ex.printStackTrace();
            }

        }

        log.info("构建所有维度维值任务运行完成");
    }

    private String getNature(List<Term> termList) {

        String nature = null;

        if (termList.size() > 1) {
            nature = "sentence";
        } else {
            for (Term term : termList) {
                nature = term.getNatureStr();
            }
        }

        return nature;

    }

    private CacheReloadTask findByKey(String key) {

        List<CacheReloadTask> cacheReloadTaskList = this.cacheReloadTaskDao.findByKey(key);
        if (!CollectionUtils.isEmpty(cacheReloadTaskList) && cacheReloadTaskList.size() > 0) {
            return cacheReloadTaskList.get(0);
        }

        return null;

    }


    @Override
    public void createCacheTask() {

        //复制缓存任务
        DynamicDataSourceContextHolder.push(JdbcDataSourceType.MYSQL.getDesc());
        List<QueryPlan> queryPlanList = this.queryPlanDao.findAll();
        if (!CollectionUtils.isEmpty(queryPlanList)) {
            for (QueryPlan queryPlan : queryPlanList) {
                String key = queryPlan.getKey();

                CacheReloadTask cacheReloadTask = this.findByKey(key);
                if (null == cacheReloadTask) {

                    CacheReloadTask newCacheTask = new CacheReloadTask();
                    newCacheTask.setCacheReloadStatus(CacheReloadStatus.WAIT);
                    newCacheTask.setMvType(MVType.WIDGET);
                    newCacheTask.setKey(key);

                    this.cacheReloadTaskDao.save(newCacheTask);

                }

            }
        }

        //所有退化维
        List<Dimension> dimensionList = this.indicatorService.listDegenerateDimension();
        if (!CollectionUtils.isEmpty(dimensionList)) {
            for (Dimension dimension : dimensionList) {

                //退化维以维度code作为唯一key
                String code = dimension.getCode();
                CacheReloadTask cacheReloadTask = this.findByKey(code);
                if (null == cacheReloadTask) {

                    CacheReloadTask newCacheTask = new CacheReloadTask();
                    newCacheTask.setCacheReloadStatus(CacheReloadStatus.WAIT);
                    newCacheTask.setMvType(MVType.DIM_DEGENERATE);
                    newCacheTask.setKey(code);

                    this.cacheReloadTaskDao.save(newCacheTask);

                }
            }
        }
    }

    public void doCacheTask(CacheReloadTask cacheReloadTask) {

        String key = cacheReloadTask.getKey();

        //枷锁查询,保证多节点任务只有一个节点可运行
        Integer status = cacheReloadTaskDao.findByLockKey(key);

        Date beforUpdateDate = cacheReloadTask.getBeforUpdateDate();
        boolean action = false;

        if (null == beforUpdateDate) {
            action = true;
        } else {

            Long beforTimeLong = beforUpdateDate.getTime();
            Long nowTimeLong = new Date().getTime();

            Long hour = 1000l * 60l * 60l;

            Long dev = nowTimeLong - beforTimeLong;

            if (dev > (2 * hour)) {
                action = true;
            }

        }

        //如果状态不为运行状态，则开始
        if (!CacheReloadStatus.RUNING.getCode().equals(status) || action) {

            cacheReloadTask.setCacheReloadStatus(CacheReloadStatus.RUNING);
            this.cacheReloadTaskDao.save(cacheReloadTask);

            try {
                this.action(cacheReloadTask);
            } catch (Exception ex) {
                ex.printStackTrace();
                cacheReloadTask.setMeassage(ex.getMessage());
                cacheReloadTask.setCacheReloadStatus(CacheReloadStatus.FAIL);

                this.doComplete(cacheReloadTask);

            }
        }
    }

    private static void sleep() {
        try {
            TimeUnit.MILLISECONDS.sleep(100);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    /**
     * 数据缓存更新开始
     */
    @Override
    public void flushCacheData() {
        //数据缓存更新
        DynamicDataSourceContextHolder.push(JdbcDataSourceType.MYSQL.getDesc());
        List<CacheReloadTask> cacheTaskList = cacheReloadTaskDao.findAll();
        for (CacheReloadTask cacheReloadTask : cacheTaskList) {
            this.doCacheTask(cacheReloadTask);
            sleep();
        }

        System.err.println("执行静态定时任务时间: " + LocalDateTime.now());
    }

    private void action(CacheReloadTask task) {

        MVType mvType = task.getMvType();
        if (MVType.DIM_DEGENERATE.equals(mvType)) {
            this.doDimDegenerate(task);
        } else if (MVType.WIDGET.equals(mvType)) {
            this.doWidget(task);
        } else {
            System.err.println("miss mvType : " + task.getKey());
        }

    }

    private void doDimDegenerate(CacheReloadTask task) {

        //退化维key值是维度code
        String dimCode = task.getKey();

        DimensionQueryParam dimQueryParam = new DimensionQueryParam();
        dimQueryParam.setCode(dimCode);
        dimQueryParam.setCacheStrategy(CacheStrategy.OVERWRITE);
        dimQueryParam.setCacheTable(false);

        PageData pageData = this.dimQueryService.execQueryDimensionValues(dimQueryParam, false);
        String mvTableName = "MV_DIM_" + dimCode;
        this.redisCacheService.put(mvTableName, pageData.getRowList());

        //设置完成
        this.doComplete(task);

    }

    private void doWidget(CacheReloadTask task) {

        //widget key是数据源md5
        String md5Key = task.getKey();

        if (md5Key.indexOf("DQP_") == 0) {

            String cacheKey = "DS_" + md5Key;
            DimensionQueryParam queryParam = redisCacheService.get(cacheKey, DimensionQueryParam.class);
            //增加缓存过期时间
            redisCacheService.put(cacheKey, queryParam);

            if (null != queryParam) {
                queryParam.setCacheStrategy(CacheStrategy.QUERY_UPDATE);
                PageData pageData = this.dimQueryService.execQueryDimensionValues(queryParam);
            }

        } else if (md5Key.indexOf("DSQ_") == 0) {

            String cacheKey = "DS_" + md5Key;
            DataSource dataSource = redisCacheService.get(cacheKey, DataSource.class);
            //增加缓存过期时间
            redisCacheService.put(cacheKey, dataSource);

            if (null != dataSource) {
                dataSource.setCacheStrategy(CacheStrategy.QUERY_UPDATE);
                this.chartQueryService.query(md5Key, dataSource);
            }

        }

        this.doComplete(task);

    }

    private void doComplete(CacheReloadTask task) {

        task.setCacheReloadStatus(CacheReloadStatus.COMPLETE);
        task.setBeforUpdateDate(task.getUpdateDate());
        task.setUpdateDate(new Date());

        DynamicDataSourceContextHolder.push(JdbcDataSourceType.MYSQL.getDesc());
        //设置完成
        this.cacheReloadTaskDao.save(task);

    }

    @Override
    public String snapshot() {
        return null;
    }
}
