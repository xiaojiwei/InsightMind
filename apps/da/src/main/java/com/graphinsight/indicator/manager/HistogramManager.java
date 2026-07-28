package com.graphinsight.indicator.manager;

import com.baomidou.dynamic.datasource.annotation.DS;
import com.baomidou.dynamic.datasource.toolkit.DynamicDataSourceContextHolder;
import com.baomidou.mybatisplus.core.toolkit.CollectionUtils;
import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.graphinsight.indicator.auto.entity.DimensionHistogram;
import com.graphinsight.indicator.auto.entity.DwTable;
import com.graphinsight.indicator.auto.entity.TableHistogram;
import com.graphinsight.indicator.auto.service.IDimensionHistogramService;
import com.graphinsight.indicator.auto.service.ITableHistogramService;
import com.graphinsight.indicator.constant.CacheConstant;
import com.graphinsight.indicator.enums.JdbcDataSourceType;
import com.graphinsight.indicator.enums.ViewType;
import com.graphinsight.indicator.enums.YesNoType;
import com.graphinsight.indicator.model.Dimension;
import com.graphinsight.indicator.model.Table;
import com.graphinsight.indicator.model.dto.HistogramCache;
import com.graphinsight.indicator.model.dto.HistogramQueryResult;
import com.graphinsight.indicator.service.IndicatorService;
import com.graphinsight.indicator.thread.DimensionHistogramQueryThread;
import com.graphinsight.indicator.thread.TableHistogramQueryThread;
import lombok.extern.slf4j.Slf4j;
import org.joda.time.DateTime;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import javax.annotation.Resource;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.Future;
import java.util.stream.Collectors;

/**
 * Date: 2023/1/4
 * Desc:
 */
@Slf4j
@Component
@DS("mysql")
public class HistogramManager {

    @Resource
    IDimensionHistogramService dimensionHistogramService;
    @Resource
    ITableHistogramService tableHistogramService;
    @Resource
    DorisQueryManager dorisQueryManager;
    @Resource
    CacheManager cacheManager;
    @Resource
    ThreadPoolTaskExecutor executor;
    @Resource
    IndicatorService indicatorService;

    private static Cache<String, HistogramCache> histogramMemCache;

    @PostConstruct
    public void init() {
        // 缓存初始化
        histogramMemCache = CacheBuilder.newBuilder()
                .build();
        loadCache();
    }

    public HistogramCache getCache() {
        HistogramCache cache = histogramMemCache.getIfPresent(CacheConstant.HISTOGRAM_CACHE_KEY);
        if (cache == null) {
            return loadCache();
        }
        return cache;
    }

    public HistogramCache loadCache() {
        histogramMemCache.invalidateAll();
        HistogramCache histogramCache = loadFromDB();
        HistogramManager.histogramMemCache.put(CacheConstant.HISTOGRAM_CACHE_KEY, histogramCache);
        return histogramCache;
    }

    private HistogramCache loadFromDB() {
        DynamicDataSourceContextHolder.push(JdbcDataSourceType.MYSQL.getDesc());
        HistogramCache histogramCache = new HistogramCache();
        List<TableHistogram> tableHistograms = tableHistogramService.list();
        List<DimensionHistogram> dimensionHistograms = dimensionHistogramService.list();
        histogramCache.setDimensionHistograms(dimensionHistograms);
        histogramCache.setTableHistograms(tableHistograms);
        return histogramCache;
    }

    public void runTable() {
        Map<Integer, DwTable> dwTableMap = cacheManager.getMetadataCache().getDwTableMap();
        Collection<DwTable> dwTables = dwTableMap.values();
        if (CollectionUtils.isNotEmpty(dwTables)) {
            List<TableHistogramQueryThread> tasks = dwTables.stream().map(table -> {
                TableHistogramQueryThread task = new TableHistogramQueryThread(this, table);
                return task;
            }).collect(Collectors.toList());
            try {
                tableHistogramService.remove(null);
                List<Future<HistogramQueryResult>> futures = executor.getThreadPoolExecutor().invokeAll(tasks);
                for (Future<HistogramQueryResult> future : futures) {
                    future.get();
                }
            } catch (InterruptedException e) {
                log.error("任务执行异常：", e);
            } catch (Exception e) {
                log.error("任务执行异常：", e);
            }
        }
    }

    @Resource
    DimensionManager dimensionManager;

    public void runDimension() {
        Map<Integer, com.graphinsight.indicator.auto.entity.Dimension> dimensionMap = cacheManager.getMetadataCache().getAllDimensionMap();
        Collection<com.graphinsight.indicator.auto.entity.Dimension> dimensions = dimensionMap.values();
        if (CollectionUtils.isNotEmpty(dimensions)) {
            dimensions = dimensions.stream()
                    .filter(dimension -> !ViewType.isDate(dimension.getViewType()))
                    .filter(dimension -> cacheManager.getDimensionCache(dimension.getId()) != null)
                    .filter(dimension -> CollectionUtils.isNotEmpty(cacheManager.getDimensionCache(dimension.getId()).getRelatedDwTableIds()))
                    .collect(Collectors.toList());
            Set<String> codes = dimensions.stream().map(dimension -> dimensionManager.getLeastSeqDimensions(dimension.getId())).map(com.graphinsight.indicator.auto.entity.Dimension::getCode).collect(Collectors.toSet());
            List<DimensionHistogramQueryThread> tasks = new ArrayList<>();
            codes.forEach(code -> {
                try {
                    Dimension info = indicatorService.getDimensionTableInfo(code);
                    List<Table> factTableList = info.getFactTableList();
                    if (CollectionUtils.isNotEmpty(factTableList)) {
                        factTableList.forEach(table -> {
                            DimensionHistogramQueryThread thread = new DimensionHistogramQueryThread(this, info, table);
                            tasks.add(thread);
                        });
                    }
                } catch (Exception e) {
                    log.error("维度信息获取异常:", e);
                }
            });
            try {
                dimensionHistogramService.remove(null);
                List<Future<HistogramQueryResult>> futures = executor.getThreadPoolExecutor().invokeAll(tasks);
                for (Future<HistogramQueryResult> future : futures) {
                    future.get();
                }
            } catch (InterruptedException e) {
                log.error("任务执行异常：", e);
            } catch (Exception e) {
                log.error("任务执行异常：", e);
            }
        }
    }

    private String buildDimensionSql(Dimension dimension, Table factTable) {
        String sql = "select count(DISTINCT `" + factTable.getFactColumn() + "` ) from " + factTable.getSchemaName() + "." + factTable.getTableName();
        if (factTable.getHasColumnDT()) {
            sql += " where dt = " + "'" + DateTime.now().plusDays(-2).toString("yyyy-MM-dd") + "'";
        }
        return sql;
    }

    private String buildTableSql(DwTable table) {
        String sql = "select count(1) from " + table.getSchemaName() + "." + table.getTableName();
        if (Objects.equals(YesNoType.YES.getCode(), table.getHasDt())) {
            sql += " where dt = " + "'" + DateTime.now().plusDays(-2).toString("yyyy-MM-dd") + "'";
        }
        return sql;
    }


    public HistogramQueryResult dimensionQuery(Dimension dimension, Table factTable) {
        HistogramQueryResult result = new HistogramQueryResult();
        String sql = buildDimensionSql(dimension, factTable);
        result.setSql(sql);
        log.info("开始执行维度统计:{},事实表:{},sql:{}", dimension.getName(), factTable.getTableName(), sql);
        Long rowNum = dorisQueryManager.exec(sql, Long.class);
        result.setTableName(factTable.getSchemaName() + "." + factTable.getTableName());
        result.setDimCode(dimension.getCode());
        result.setRowNum(rowNum);
        return result;
    }

    public void saveDimensionNum(HistogramQueryResult result) {
        DynamicDataSourceContextHolder.push(JdbcDataSourceType.MYSQL.getDesc());
        DimensionHistogram histogram = new DimensionHistogram();
        histogram.setUpdateTime(LocalDateTime.now());
        histogram.setCreateTime(LocalDateTime.now());
        histogram.setRowNum(result.getRowNum());
        histogram.setTableName(result.getTableName());
        histogram.setDimCode(result.getDimCode());
        dimensionHistogramService.save(histogram);
    }


    public void saveTableNum(HistogramQueryResult result) {
        DynamicDataSourceContextHolder.push(JdbcDataSourceType.MYSQL.getDesc());
        TableHistogram tableHistogram = new TableHistogram();
        tableHistogram.setUpdateTime(LocalDateTime.now());
        tableHistogram.setCreateTime(LocalDateTime.now());
        tableHistogram.setRowNum(result.getRowNum());
        tableHistogram.setTableName(result.getTableName());
        tableHistogramService.save(tableHistogram);
    }

    public HistogramQueryResult tableQuery(DwTable table) {
        HistogramQueryResult result = new HistogramQueryResult();
        String sql = buildTableSql(table);
        log.info("开始执行事实表统计,事实表:{},sql:{}", table.getTableName(), sql);
        result.setSql(sql);
        Long rowNum = dorisQueryManager.exec(sql, Long.class);
        result.setTableName(table.getSchemaName() + "." + table.getTableName());
        result.setRowNum(rowNum);
        return result;
    }


}
