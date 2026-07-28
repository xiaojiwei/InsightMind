package com.graphinsight.indicator.manager;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.baomidou.dynamic.datasource.annotation.DS;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.Multimap;
import com.graphinsight.indicator.auto.entity.BaseConfigure;
import com.graphinsight.indicator.auto.entity.Category;
import com.graphinsight.indicator.auto.entity.ComplexMeasureDependencyTree;
import com.graphinsight.indicator.auto.entity.Dashboard;
import com.graphinsight.indicator.auto.entity.DataSource;
import com.graphinsight.indicator.auto.entity.Dimension;
import com.graphinsight.indicator.auto.entity.DimensionApplication;
import com.graphinsight.indicator.auto.entity.DimensionDimtableConnect;
import com.graphinsight.indicator.auto.entity.DimensionFilter;
import com.graphinsight.indicator.auto.entity.DimensionOperator;
import com.graphinsight.indicator.auto.entity.DimensionOperatorValue;
import com.graphinsight.indicator.auto.entity.DwColumn;
import com.graphinsight.indicator.auto.entity.DwTable;
import com.graphinsight.indicator.auto.entity.Hierarchy;
import com.graphinsight.indicator.auto.entity.Level;
import com.graphinsight.indicator.auto.entity.Measure;
import com.graphinsight.indicator.auto.entity.MeasureApplication;
import com.graphinsight.indicator.auto.entity.MeasureNaturalDateMapping;
import com.graphinsight.indicator.auto.entity.User;
import com.graphinsight.indicator.auto.entity.Widget;
import com.graphinsight.indicator.auto.entity.WidgetDetail;
import com.graphinsight.indicator.auto.mapper.CategoryMapper;
import com.graphinsight.indicator.auto.mapper.ComplexMeasureDependencyTreeMapper;
import com.graphinsight.indicator.auto.mapper.DimensionApplicationMapper;
import com.graphinsight.indicator.auto.mapper.DimensionDimtableConnectMapper;
import com.graphinsight.indicator.auto.mapper.DimensionFilterMapper;
import com.graphinsight.indicator.auto.mapper.DimensionMapper;
import com.graphinsight.indicator.auto.mapper.DimensionOperatorMapper;
import com.graphinsight.indicator.auto.mapper.DimensionOperatorValueMapper;
import com.graphinsight.indicator.auto.mapper.DwColumnMapper;
import com.graphinsight.indicator.auto.mapper.DwTableMapper;
import com.graphinsight.indicator.auto.mapper.HierarchyMapper;
import com.graphinsight.indicator.auto.mapper.LevelMapper;
import com.graphinsight.indicator.auto.mapper.MeasureApplicationMapper;
import com.graphinsight.indicator.auto.mapper.MeasureMapper;
import com.graphinsight.indicator.auto.service.IBaseConfigureService;
import com.graphinsight.indicator.auto.service.IDashboardService;
import com.graphinsight.indicator.auto.service.IDataSourceService;
import com.graphinsight.indicator.auto.service.IMeasureNaturalDateMappingService;
import com.graphinsight.indicator.auto.service.IUserService;
import com.graphinsight.indicator.auto.service.IWidgetDetailService;
import com.graphinsight.indicator.auto.service.IWidgetService;
import com.graphinsight.indicator.constant.CacheConstant;
import com.graphinsight.indicator.enums.FactTableType;
import com.graphinsight.indicator.enums.MeasureType;
import com.graphinsight.indicator.enums.SqlLogicalType;
import com.graphinsight.indicator.enums.SqlOprType;
import com.graphinsight.indicator.enums.TableColumnType;
import com.graphinsight.indicator.enums.TimeRange;
import com.graphinsight.indicator.enums.YesNoType;
import com.graphinsight.indicator.model.Filter;
import com.graphinsight.indicator.model.Operator;
import com.graphinsight.indicator.model.cache.DimensionCache;
import com.graphinsight.indicator.model.cache.DwTableCache;
import com.graphinsight.indicator.model.cache.MeasureApplicationCache;
import com.graphinsight.indicator.model.cache.MeasureApplicationDependency;
import com.graphinsight.indicator.model.cache.MeasureCache;
import com.graphinsight.indicator.model.cache.MeasureDependencyTreeInfo;
import com.graphinsight.indicator.model.cache.MetadataCache;
import com.graphinsight.indicator.service.DimensionQueryService;
import com.graphinsight.indicator.service.RedisCacheService;
import com.graphinsight.indicator.util.IndicatorCollectionUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;

import javax.annotation.PostConstruct;
import javax.annotation.Resource;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

/**
 * @Description:
 * @Date: 2021/11/22
 */
@Service
@Slf4j
@DS("mysql")
public class CacheManager {

    @Autowired
    private MeasureMapper measureMapper;
    @Autowired
    private DimensionMapper dimensionMapper;
    @Autowired
    private DwTableMapper dwTableMapper;
    @Autowired
    private MeasureApplicationMapper measureApplicationMapper;
    @Autowired
    private DimensionApplicationMapper dimensionApplicationMapper;
    @Autowired
    private CategoryMapper categoryMapper;
    @Autowired
    private DimensionDimtableConnectMapper dimensionDimtableConnectMapper;
    @Autowired
    private RedisCacheService redisCacheService;
    @Autowired
    private SpaceManager spaceManager;
    @Value("${debugging:off}")
    private String debugging;


    private static Cache<String, Object> buildingCache;
    private static Cache<String, Object> rollbackCache;
    private static Cache<String, Object> onlineCache;

    private static MetadataCache metadataCache = null;

    public DwTableCache getDwTableCache(Integer tableId) {
        return Optional.ofNullable(onlineCache.getIfPresent(CacheConstant.DWTABLE_CACHE_PREFIX + tableId))
                .map(o -> {
                    if (o instanceof DwTableCache) {
                        return (DwTableCache) o;
                    } else if (o instanceof JSONObject) {
                        return JSON.toJavaObject((JSONObject) o, DwTableCache.class);
                    }
                    return null;
                })
                .orElse(null);
    }

    public MetadataCache getMetadataCache() {
        if (metadataCache == null || isExpired()) {
            metadataCache = Optional.ofNullable(onlineCache.getIfPresent(CacheConstant.METADATA_CACHE_KEY))
                    .map(o -> {
                        if (o instanceof MetadataCache) {
                            return (MetadataCache) o;
                        } else if (o instanceof JSONObject) {
                            return JSON.toJavaObject((JSONObject) o, MetadataCache.class);
                        }
                        return null;
                    })
                    .orElse(null);
        }
        return metadataCache;

    }

    public MeasureCache getBuildingMeasureCache(Integer measureId) {
        return Optional.ofNullable(buildingCache.getIfPresent(CacheConstant.MEASURE_CACHE_PREFIX + measureId))
                .map(o -> {
                    if (o instanceof MeasureCache) {
                        return (MeasureCache) o;
                    } else if (o instanceof JSONObject) {
                        return JSON.toJavaObject((JSONObject) o, MeasureCache.class);
                    }
                    return null;
                })
                .orElse(null);
    }

    public MeasureCache getMeasureCache(Integer measureId) {
        return Optional.ofNullable(onlineCache.getIfPresent(CacheConstant.MEASURE_CACHE_PREFIX + measureId))
                .map(o -> {
                    if (o instanceof MeasureCache) {
                        return (MeasureCache) o;
                    } else if (o instanceof JSONObject) {
                        return JSON.toJavaObject((JSONObject) o, MeasureCache.class);
                    }
                    return null;
                })
                .orElse(null);
    }

    public DimensionCache getDimensionCache(Integer dimensionId) {
        return Optional.ofNullable(onlineCache.getIfPresent(CacheConstant.DIMSENSION_CACHE_PREFIX + dimensionId))
                .map(o -> {
                    if (o instanceof DimensionCache) {
                        return (DimensionCache) o;
                    } else if (o instanceof JSONObject) {
                        return JSON.toJavaObject((JSONObject) o, DimensionCache.class);
                    }
                    return null;
                })
                .orElse(null);
    }

    public DimensionCache getBuildingDimensionCache(Integer dimensionId) {
        return Optional.ofNullable(buildingCache.getIfPresent(CacheConstant.DIMSENSION_CACHE_PREFIX + dimensionId))
                .map(o -> {
                    if (o instanceof DimensionCache) {
                        return (DimensionCache) o;
                    } else if (o instanceof JSONObject) {
                        return JSON.toJavaObject((JSONObject) o, DimensionCache.class);
                    }
                    return null;
                })
                .orElse(null);
    }


    public boolean isExpired() {
        Long redisVersion = redisCacheService.getLong(CacheConstant.CACHE_VERSION_KEY);
        Object localVersion = Optional.ofNullable(onlineCache.getIfPresent(CacheConstant.CACHE_VERSION_KEY))
                .orElse(null);
        log.info("redis版本号:{},本地缓存版本号:{}", redisVersion, localVersion);
        if (Objects.isNull(redisVersion) || Objects.isNull(localVersion)) {
            return false;
        }
        return !Objects.equals(redisVersion.toString(), localVersion.toString());
    }

    public void refreshFromRedis() {
        try {
            loadFromDB();
            if (!Objects.equals("on", debugging)) {
                JSONObject redisCache = redisCacheService.getJSONObject(CacheConstant.ONLINE_CACHE_KEY);
                if (Objects.nonNull(redisCache)) {
                    // redis不为空且不是debug模式，才使用redis缓存
                    buildingCache.putAll(redisCache);
                } else {
                    loadFromDB();
                }
            } else {
                loadFromDB();
            }
        } catch (Exception e) {
            log.error("redis读取缓存异常:", e);
            log.info("redis读取缓存异常,从DB load数据");
            loadFromDB();
        }
        // 同步版本号
        Long version = redisCacheService.getLong(CacheConstant.CACHE_VERSION_KEY);
        onlineCache.putAll(buildingCache.asMap());
        rollbackCache.putAll(buildingCache.asMap());
        onlineCache.put(CacheConstant.CACHE_VERSION_KEY, version);
    }

    public void refreshFromDB() {
        loadFromDB();
        Long version = redisCacheService.getLong(CacheConstant.CACHE_VERSION_KEY);
        onlineCache.put(CacheConstant.CACHE_VERSION_KEY, version);
        onlineCache.putAll(buildingCache.asMap());
        rollbackCache.putAll(buildingCache.asMap());
    }

    public void refreshCache() {
        if (Objects.equals("on", debugging)) {
            refreshFromDB();
        } else {
            refreshFromRedis();
        }
    }

    public JSONObject getFormRedis() {
        try {
            JSONObject redisCache = redisCacheService.getJSONObject(CacheConstant.ONLINE_CACHE_KEY);
            // Long newVersionNum = redisCacheService.getLong(CacheConstant.CACHE_VERSION_KEY);
            // // 更新线上缓存
            // onlineCache.put(CacheConstant.CACHE_VERSION_KEY, newVersionNum);
            // onlineCache.putAll(redisCache.getInnerMap());
            return redisCache;
        } catch (Exception e) {
            log.error("redis读取缓存异常:", e);
        }
        return null;
    }

    @PostConstruct
    public void init() {
        // 缓存初始化
        buildingCache = CacheBuilder.newBuilder()
                .build();
        rollbackCache = CacheBuilder.newBuilder()
                .build();
        onlineCache = CacheBuilder.newBuilder()
                .build();
        // 初始化缓存版本号
        redisCacheService.setIfAbsent(CacheConstant.CACHE_VERSION_KEY, CacheConstant.CACHE_VERSION_INIT_VALUE);
        refreshCache();
    }

    public Cache getCache() {
        return onlineCache;
    }

    public void reloadCache() {
        // 将回滚缓存更新至当前线上缓存
        rollbackCache.putAll(onlineCache.asMap());

        /**
         * 异步刷新缓存
         */
        CompletableFuture.runAsync(() -> {
            doReload();
        });
    }

    /**
     * 同步加载缓存
     */
    public void syncReloadCache() {
        // 将回滚缓存更新至当前线上缓存
        rollbackCache.putAll(onlineCache.asMap());
        doReload();
    }

    private void doReload() {
        log.info(Thread.currentThread() + "cache reload start ...");
        buildingCache.invalidateAll();
        loadFromDB();
        // redis版本号自增
        Long oldVersion = redisCacheService.getLong(CacheConstant.CACHE_VERSION_KEY);
        if (Objects.isNull(oldVersion)) {
            redisCacheService.setIfAbsent(CacheConstant.CACHE_VERSION_KEY, CacheConstant.CACHE_VERSION_INIT_VALUE);
        }
        redisCacheService.increment(CacheConstant.CACHE_VERSION_KEY);
        Long newVersionNum = redisCacheService.getLong(CacheConstant.CACHE_VERSION_KEY);
        // 更新线上缓存
        onlineCache.invalidateAll();
        onlineCache.putAll(buildingCache.asMap());

        // 更新本地缓存版本号
        onlineCache.put(CacheConstant.CACHE_VERSION_KEY, newVersionNum);
        if (!Objects.equals("on", debugging)) {
            // 不是本地调试才刷新redis
            refreshRedis();
        }
        log.info(Thread.currentThread() + "cache reload end ");
    }

    private void refreshRedis() {
        // 将最新 onlineCache存储到redis
        try {
            redisCacheService.permanentPut(CacheConstant.ONLINE_CACHE_KEY, JSON.toJSONString(buildingCache.asMap()));
        } catch (Exception e) {
            log.error("刷新redis缓存异常:", e);
        }
    }

    @Autowired
    private ComplexMeasureDependencyTreeMapper complexMeasureDependencyTreeMapper;


    public List<MeasureDependencyTreeInfo> buildDependencyTree(Map<Integer, List<MeasureApplication>> measIdAppList, Map<Integer, Measure> measureMap) {
        List<MeasureDependencyTreeInfo> result = new ArrayList<>();

        // 查询依赖关系
        List<com.graphinsight.indicator.auto.entity.ComplexMeasureDependencyTree> complexMeasureDependencyTrees = complexMeasureDependencyTreeMapper.selectList(null);
        Map<Integer, List<com.graphinsight.indicator.auto.entity.ComplexMeasureDependencyTree>> measIdDenpendencyTreeMap = complexMeasureDependencyTrees.stream().collect(Collectors.groupingBy(com.graphinsight.indicator.auto.entity.ComplexMeasureDependencyTree::getComplexMeasId));
        Map<Integer, List<com.graphinsight.indicator.auto.entity.ComplexMeasureDependencyTree>> measAppIdDenpendencyTreeMap = complexMeasureDependencyTrees.stream().collect(Collectors.groupingBy(com.graphinsight.indicator.auto.entity.ComplexMeasureDependencyTree::getMeasAppId));
        measIdDenpendencyTreeMap.forEach((measId, v) -> {
            Map<Integer, List<com.graphinsight.indicator.auto.entity.ComplexMeasureDependencyTree>> collect = v.stream().collect(Collectors.groupingBy(com.graphinsight.indicator.auto.entity.ComplexMeasureDependencyTree::getMeasAppId));
            List<MeasureApplicationDependency> measureApplicationDependencyList = new ArrayList<>();
            collect.forEach((measAppId, list) -> {
                MeasureApplicationDependency mad = new MeasureApplicationDependency();
                mad.setMeasId(measId);
                mad.setMeasAppId(measAppId);
                bulidMeasureDependency(measAppId, measAppIdDenpendencyTreeMap, measIdAppList, mad);
                Set<Integer> relyMeasIds = list.stream().filter(tree -> Objects.equals(tree.getDependencyType(), TableColumnType.MEASURE.getCode())).map(ComplexMeasureDependencyTree::getDependencyId).collect(Collectors.toSet());
                Set<Integer> relyDimIds = list.stream().filter(tree -> Objects.equals(tree.getDependencyType(), TableColumnType.DIMENSION.getCode())).map(ComplexMeasureDependencyTree::getDependencyId).collect(Collectors.toSet());
                mad.setDependencyMeasIds(relyMeasIds);
                mad.setDependencyDimIds(relyDimIds);
                measureApplicationDependencyList.add(mad);
            });
            MeasureDependencyTreeInfo complexMeasureDependencyTree = new MeasureDependencyTreeInfo(measId, measureApplicationDependencyList);
            if (measureMap.get(measId) != null) {
                complexMeasureDependencyTree.setCode(measureMap.get(measId).getCode());
                result.add(complexMeasureDependencyTree);
            }
        });
        return result;

    }

    public void bulidMeasureDependency(Integer measAppId, Map<Integer, List<com.graphinsight.indicator.auto.entity.ComplexMeasureDependencyTree>> measAppIdDenpendencyTreeMap, Map<Integer, List<MeasureApplication>> measIdAppList, MeasureApplicationDependency measureApplicationDependency) {
        List<com.graphinsight.indicator.auto.entity.ComplexMeasureDependencyTree> complexMeasureDependencyTrees = measAppIdDenpendencyTreeMap.get(measAppId);
        if (CollectionUtils.isEmpty(complexMeasureDependencyTrees)) {
            log.error("计算指标应用ID:{},对应的依赖树为空", measAppId);
            return;
        }
        Map<Integer, List<com.graphinsight.indicator.auto.entity.ComplexMeasureDependencyTree>> map = complexMeasureDependencyTrees.stream().collect(Collectors.groupingBy(com.graphinsight.indicator.auto.entity.ComplexMeasureDependencyTree::getMeasAppId));
        map.forEach((k, v) -> {
            v.forEach(tree -> {
                Integer dependencyId = tree.getDependencyId();
                if (Objects.equals(tree.getDependencyType(), TableColumnType.DIMENSION.getCode())) {
                    measureApplicationDependency.getDependencyBaseDimIds().add(dependencyId);
                } else if (Objects.equals(tree.getDependencyType(), TableColumnType.MEASURE.getCode())) {
                    // measureApplicationDependency.getDependencyBaseMeasIds().add(dependencyId);
                    Collection<MeasureApplication> measureApplications = measIdAppList.get(dependencyId);
                    if (!CollectionUtils.isEmpty(measureApplications)) {
                        MeasureApplication dependencyMeasureApp = measureApplications.stream().findFirst().orElse(null);
                        // MeasureApplication dependencyMeasureApp = measureApplications.stream().filter(m -> Objects.equals(m.getId(), tree.getDependencyMeasAppId())).findFirst().orElse(null);
                        if (dependencyMeasureApp != null) {
                            if (Objects.equals(MeasureType.DERIVED.getCode(), dependencyMeasureApp.getApplyType()) || Objects.equals(MeasureType.EXTENDED.getCode(), dependencyMeasureApp.getApplyType())) {
                                // 复合指标
                                bulidMeasureDependency(dependencyMeasureApp.getId(), measAppIdDenpendencyTreeMap, measIdAppList, measureApplicationDependency);
                            } else {
                                // 原子指标
                                measureApplicationDependency.getDependencyBaseMeasIds().add(dependencyId);
                            }
                        } else {
                            log.warn("指标应用ID" + tree.getDependencyMeasAppId() + "不存在");
                        }
                    } else {
                        log.warn("指标" + dependencyId + "不存在对应的应用表");
                    }

                }
            });

        });
    }

    private boolean isComplexMeasure(Integer applyType) {
        return Objects.equals(MeasureType.DERIVED.getCode(), applyType) || Objects.equals(MeasureType.EXTENDED.getCode(), applyType);
    }

    @Autowired
    private LevelMapper levelMapper;

    /**
     * 缓存指标基础信息
     *
     * @param measures         指标列表
     * @param measIdAppList    指标应用信息
     * @param tableIdDimAppMap 维度应用表信息
     */
    private void cacheOriginMeasureInfo(List<Measure> measures,
                                        Map<Integer, List<MeasureApplication>> measIdAppList,
                                        Multimap<Integer, DimensionApplication> tableIdDimAppMap,
                                        Map<Integer, Level> dimIdLevelMap,
                                        Map<Integer, List<Level>> hierarchyIdLevelMap,
                                        Map<Integer, Dimension> dimensionMap,
                                        Map<Integer, DwTable> dwTableMap,
                                        Map<Long, List<MeasureNaturalDateMapping>> measIdNaturalDateMappingMap) {
        // 缓存基础指标信息
        Map<Integer, Measure> measureMap = new HashMap<>();
        measures.forEach(m -> {
            Set<Integer> relatedTableIds = new HashSet<>(); //指标相关模型
            List<Integer> detailTableIds = new ArrayList<>(); //指标相关明细表
            Set<Integer> measureAppIds = new HashSet<>(); //指标相关应用
            Set<Integer> relatedDimIds = new HashSet<>();// 指标相关维度
            List<MeasureApplicationCache> measureApplicationCaches = new ArrayList<>();

            Collection<MeasureApplication> measAppList = measIdAppList.get(m.getId());
            measAppList.forEach(measureApplication -> {
                measureAppIds.add(measureApplication.getId());
                if (!isComplexMeasure(measureApplication.getApplyType())) {
                    // 原子指标
                    if (measureApplication.getDwTableId() != null) {
                        relatedTableIds.add(measureApplication.getDwTableId());
                        DwTable dwTable = dwTableMap.get(measureApplication.getDwTableId());
                        if (Objects.nonNull(dwTable)) {
                            if (Objects.equals(dwTable.getFactTableType(), FactTableType.DETAIL.getCode())) {
                                detailTableIds.add(measureApplication.getDwTableId());
                            }
                        }
                        // 相关维度增加自然日期
                        List<MeasureNaturalDateMapping> measureNaturalDateMappings = measIdNaturalDateMappingMap.get(m.getId().longValue());
                        if (!CollectionUtils.isEmpty(measureNaturalDateMappings)) {
                            for (MeasureNaturalDateMapping measureNaturalDateMapping : measureNaturalDateMappings) {
                                if (measureNaturalDateMapping != null && measureNaturalDateMapping.getDwTableId().intValue() == measureApplication.getDwTableId().intValue()) {
                                    relatedDimIds.add(measureNaturalDateMapping.getNaturalDimId().intValue());
                                }
                            }
                        }
                    }
                    MeasureApplicationCache measureApplicationCache = new MeasureApplicationCache();
                    measureApplicationCache.setRelatedDwTableId(measureApplication.getDwTableId());
                    measureApplicationCache.setApplyType(measureApplication.getApplyType());
                    measureApplicationCache.setMeasAppId(measureApplication.getId());
                    measureApplicationCache.setMeasId(measureApplication.getMeasId());
                    measureApplicationCache.setDataFormatStr(measureApplication.getDataFormatStr());
                    measureApplicationCache.setDecimalPlaces(measureApplication.getDecimalPlaces());
                    measureApplicationCache.setDataScale(measureApplication.getDataScale());
                    measureApplicationCaches.add(measureApplicationCache);
                }
            });
            relatedTableIds.forEach(tId -> {
                final Collection<DimensionApplication> list = tableIdDimAppMap.get(tId);
                Set<Integer> collect = list.stream().map(DimensionApplication::getDimId).collect(Collectors.toSet());
                relatedDimIds.addAll(collect);
            });

            // 关联级联维度
            Set<Integer> cascadeDimIds = new HashSet<>();// 指标相关维度

            relatedDimIds.forEach(dimId -> {
                Level level = dimIdLevelMap.get(dimId);
                if (level != null) {
                    Integer hierarchyId = level.getHierarchyId();
                    List<Level> cascadeLevel = hierarchyIdLevelMap.get(hierarchyId);
                    List<Integer> itemCascadeDimIds = cascadeLevel.stream().filter(l -> l.getSequence() < level.getSequence()).map(Level::getDimId).collect(Collectors.toList());
                    if (!CollectionUtils.isEmpty(itemCascadeDimIds)) {
                        cascadeDimIds.addAll(itemCascadeDimIds);
                    }
                }
            });

            relatedDimIds.addAll(cascadeDimIds);
            MeasureCache measureCache = new MeasureCache();
            measureCache.setRelatedDimensionIds(relatedDimIds);
            final List<String> cnNames = relatedDimIds.stream().map(id -> dimensionMap.get(id).getCnName()).collect(Collectors.toList());
            measureCache.setRelatedDimensionCnNames(cnNames);
            measureCache.setRelatedDwTableIds(relatedTableIds);
            measureCache.setDetailDwTableIds(detailTableIds);
            measureCache.setCode(m.getCode());
            measureCache.setId(m.getId());
            measureCache.setMeasureAppIds(measureAppIds);
            measureCache.setMeasure(m);
            measureCache.setMeasureApplicationCacheList(measureApplicationCaches);
            buildingCache.put(CacheConstant.MEASURE_CACHE_PREFIX + m.getId(), measureCache);
            log.info("cache measure info : {}", JSON.toJSONString(measureCache));
            measureMap.put(m.getId(), m);
        });
    }

    /**
     * 缓存所有的计算指标信息
     *
     * @param complexMeasureApplications      计算指标应用表
     * @param dimIds                          所有的维度ID
     * @param complexMeasureDependencyTreeMap 指标依赖树信息
     */
    private void cacheComplexMeasureInfo(List<MeasureApplication> complexMeasureApplications,
                                         Set<Integer> dimIds, Map<Integer, MeasureDependencyTreeInfo> complexMeasureDependencyTreeMap,
                                         Map<Integer, Measure> measureMap,
                                         Map<Integer, Dimension> dimensionMap,
                                         Map<Integer, List<DwColumn>> dwTableIdColumnsMap,
                                         Map<Integer, DwTable> dwTables) {
        complexMeasureApplications.forEach(cma -> {
            cacheSingleComplexMeasureInfo(cma, dimIds, complexMeasureDependencyTreeMap, measureMap, dimensionMap, dwTableIdColumnsMap, dwTables);
        });
    }

    private void cacheSingleComplexMeasureInfo(MeasureApplication cma,
                                               Set<Integer> dimIds,
                                               Map<Integer, MeasureDependencyTreeInfo> complexMeasureDependencyTreeMap,
                                               Map<Integer, Measure> measureMap,
                                               Map<Integer, Dimension> dimensionMap,
                                               Map<Integer, List<DwColumn>> dwTableIdColumnsMap,
                                               Map<Integer, DwTable> dwTables) {
        Set<Integer> commonDimIds = new HashSet<>();// 计算指标依赖的基础指标的公共维度
        commonDimIds.addAll(dimIds);
        Integer measId = cma.getMeasId();
        MeasureCache measureCache = Optional.ofNullable(buildingCache.getIfPresent(CacheConstant.MEASURE_CACHE_PREFIX + measId))
                .map(o -> (MeasureCache) o)
                .orElse(new MeasureCache());
        if (isComplexMeasure(cma.getApplyType())) {
            List<MeasureApplicationCache> measureApplicationCacheList = measureCache.getMeasureApplicationCacheList() == null ? new ArrayList<>() : measureCache.getMeasureApplicationCacheList();
            Set<Integer> relatedDimensionIds = measureCache.getRelatedDimensionIds();
            MeasureDependencyTreeInfo complexMeasureDependencyTree = complexMeasureDependencyTreeMap.get(cma.getMeasId());
            if (complexMeasureDependencyTree != null && complexMeasureDependencyTree.getMeasureApplicationDependencyList() != null) {
                MeasureApplicationDependency measureApplicationDependency = complexMeasureDependencyTree.getMeasureApplicationDependencyList().stream()
                        .filter(mad -> Objects.equals(mad.getMeasAppId(), cma.getId()))
                        .findFirst()
                        .orElse(null);
                if (measureApplicationDependency != null && !CollectionUtils.isEmpty(measureApplicationDependency.getDependencyBaseMeasIds())) {
                    // 依赖树存在并且依赖的基础指标存在
                    Set<Integer> dependencyBaseMeasIds = measureApplicationDependency.getDependencyBaseMeasIds();
                    dependencyBaseMeasIds.forEach(dbm -> {
                        Set<Integer> dmbReleatedDimIds = Optional.ofNullable(buildingCache.getIfPresent(CacheConstant.MEASURE_CACHE_PREFIX + dbm))
                                .map(o -> (MeasureCache) o)
                                .map(c -> c.getRelatedDimensionIds())
                                .orElse(Collections.EMPTY_SET);
                        commonDimIds.retainAll(dmbReleatedDimIds);
                    });
                    // 计算指标依赖的所有基础维度
                    Set<Integer> dependencyBaseDimIds = measureApplicationDependency.getDependencyBaseDimIds();
                    if (!CollectionUtils.isEmpty(dependencyBaseDimIds) && !commonDimIds.containsAll(dependencyBaseDimIds)) {
                        // 根据依赖的基础指标计算出来的公共维度，如果不包含依赖的基础维度，说明表达式已经出问题了，此时计算指标的相关维度应该清空
                        log.warn("计算指标:[{}]依赖的基础指标:{}产生的公共维度集合:{} 不包含其依赖的基础维度:{},出现次异常的原因可能是表达式修改造成的",
                                measureMap.get(measId).getCnName(),
                                dependencyBaseMeasIds.stream().map(id -> measureMap.get(id) == null ? id : measureMap.get(id).getCnName()).collect(Collectors.toList()),
                                commonDimIds.stream().map(id -> dimensionMap.get(id) == null ? id : dimensionMap.get(id).getCnName()).collect(Collectors.toList()),
                                dependencyBaseDimIds.stream().map(id -> dimensionMap.get(id) == null ? id : dimensionMap.get(id).getCnName()).collect(Collectors.toList()));
                        commonDimIds.clear();
                    }
                    dwTables.forEach((k, v) -> {
                        DwTableCache dwTableCache = Optional.ofNullable(buildingCache.getIfPresent(CacheConstant.DWTABLE_CACHE_PREFIX + k))
                                .map(o -> (DwTableCache) o)
                                .orElse(null);
                        if (Objects.nonNull(dwTableCache)) {
                            Set<Integer> tableRelatedDimensionIds = dwTableCache.getRelatedDimensionIds();
                            Set<Integer> tableRelatedMeasureIds = dwTableCache.getRelatedMeasureIds();
                            // if(tableRelatedDimensionIds.containsAll(dependencyBaseDimIds) && !CollectionUtils.isEmpty(dependencyBaseDimIds) && tableRelatedMeasureIds.containsAll(dependencyBaseMeasIds)){
                            // if (tableRelatedDimensionIds.containsAll(dependencyBaseDimIds) && tableRelatedMeasureIds.containsAll(dependencyBaseMeasIds)) {
                            if ((CollectionUtils.isEmpty(dependencyBaseDimIds) || tableRelatedDimensionIds.containsAll(dependencyBaseDimIds)) && containsAny(tableRelatedMeasureIds, dependencyBaseMeasIds)) {
                                MeasureApplicationCache measureApplicationCache = new MeasureApplicationCache();
                                measureApplicationCache.setRelatedDwTableId(k);
                                measureApplicationCache.setApplyType(cma.getApplyType());
                                measureApplicationCache.setMeasId(cma.getMeasId());
                                measureApplicationCache.setMeasAppId(cma.getId());
                                measureCache.getRelatedDwTableIds().add(k);
                                measureApplicationCacheList.add(measureApplicationCache);
                            }
                        }
                    });
                    Set<Integer> resultCommonDimIds = commonDimIds.stream().filter(id -> hasCommonDwTable(id, dependencyBaseDimIds)).collect(Collectors.toSet());
                    relatedDimensionIds.addAll(resultCommonDimIds);
                }
                // else {
                //     relatedDimensionIds.addAll(commonDimIds);
                // }
            } else {
                log.warn("计算指标对应的依赖树不存在,指标ID:{}", cma.getMeasId());
            }
            final List<String> cnNames = relatedDimensionIds.stream().map(id -> dimensionMap.get(id).getCnName()).collect(Collectors.toList());
            measureCache.setRelatedDimensionCnNames(cnNames);
            measureCache.setCnName(measureMap.get(cma.getMeasId()).getCnName());
            measureCache.setMeasure(measureMap.get(cma.getMeasId()));
            measureCache.setMeasureApplicationCacheList(measureApplicationCacheList);
            buildingCache.put(CacheConstant.MEASURE_CACHE_PREFIX + measId, measureCache);
            log.info("cache complex measure info : {}", JSON.toJSONString(measureCache));
        }
    }


    private boolean containsAny(Collection<? extends Object> target, Collection<? extends Object> source) {
        if (CollectionUtils.isEmpty(target)) {
            return false;
        }
        if (CollectionUtils.isEmpty(source)) {
            return true;
        }

        for (Object item : source) {
            if (target.contains(item)) {
                return true;
            }
        }
        return false;
    }


    /**
     * 判断维度是否有公共的事实表
     *
     * @param targetDimId  目标维度
     * @param sourceDimIds 具有公共事实表的维度集合
     *                     计算指标的相关维度要和其依赖的相关基础维度能够在同一个事实表里出现
     *                     比如 原子指标订单量可以从两个事实表A、B里面出
     *                     事实表A有维度城市、品牌 事实表B有类型
     *                     对于订单量，可选的指标就会有三个，城市、品牌、类型
     *                     而派生指标：城市_订单量，可选的指标就只能是城市、品牌。因为一旦带有城市维度了，这个指标就只能从事实表A出
     *                     所以需要在派生指标的相关维度中，把类型剔除
     * @return
     */
    private boolean hasCommonDwTable(Integer targetDimId, Set<Integer> sourceDimIds) {
        if (CollectionUtils.isEmpty(sourceDimIds)) {
            return true;
        }
        MetadataCache metadataCache = Optional.ofNullable(buildingCache.getIfPresent(CacheConstant.METADATA_CACHE_KEY))
                .map(o -> (MetadataCache) o)
                .orElse(null);
        if (Objects.isNull(metadataCache)) {
            log.error("事实表在缓存中不存在");
            return false;
        }
        Map<Integer, DwTable> dwTableMap = metadataCache.getDwTableMap();
        for (Integer tableId : dwTableMap.keySet()) {
            Set<Integer> relatedDimensionIds = Optional.ofNullable(buildingCache.getIfPresent(CacheConstant.DWTABLE_CACHE_PREFIX + tableId))
                    .map(o -> (DwTableCache) o)
                    .map(c -> c.getRelatedDimensionIds())
                    .orElse(null);
            if (!CollectionUtils.isEmpty(relatedDimensionIds) && relatedDimensionIds.contains(targetDimId) && relatedDimensionIds.containsAll(sourceDimIds)) {
                return true;
            }
        }
        return false;
    }


    @Autowired
    private DimensionFilterMapper dimensionFilterMapper;
    @Autowired
    private DimensionOperatorMapper dimensionOperatorMapper;
    @Autowired
    private DimensionOperatorValueMapper dimensionOperatorValueMapper;

    private List<Filter> buildFilters(List<DimensionFilter> dimensionFilterList,
                                      Map<Long, List<DimensionOperator>> filterIdOperatorsMap,
                                      Map<Long, List<DimensionOperatorValue>> operatorIdValuesMap) {
        return dimensionFilterList.stream().sorted(Comparator.comparing(DimensionFilter::getSeq)).map(df -> {
            Filter filter = new Filter();
            filter.setSqlLogicalType(SqlLogicalType.getTypeByCode(df.getSqlLogicalType()));
            filter.setCode(df.getDimCode());
            List<DimensionOperator> dos = filterIdOperatorsMap.get(df.getId());
            if (!CollectionUtils.isEmpty(dos)) {
                List<Operator> operatorList = dos.stream().sorted(Comparator.comparing(DimensionOperator::getSeq)).map(dimOper -> {
                    Operator operator = new Operator();
                    BeanUtils.copyProperties(dimOper, operator);
                    operator.setSqlLogicalType(SqlLogicalType.getTypeByCode(dimOper.getSqlLogicalType()));
                    operator.setTimeRange(TimeRange.getTypeByCode(dimOper.getTimeRange()));
                    operator.setSqlOprType(SqlOprType.getTypeByCode(dimOper.getSqlOprType()));
                    List<DimensionOperatorValue> valueList = operatorIdValuesMap.get(dimOper.getId());
                    if (!CollectionUtils.isEmpty(valueList)) {
                        operator.setDataList(valueList.stream().map(DimensionOperatorValue::getValue).collect(Collectors.toList()));
                    } else {
                        log.warn("dataList为空,DimOperatorId:", dimOper.getId());
                    }
                    return operator;
                }).collect(Collectors.toList());
                filter.setOperatorList(operatorList);
            } else {
                log.warn("operatorList,DimFilterId:", df.getId());
            }
            return filter;
        }).collect(Collectors.toList());
    }

    @Autowired
    private DwColumnMapper dwColumnMapper;
    @Autowired
    private HierarchyMapper hierarchyMapper;
    @Autowired
    private IMeasureNaturalDateMappingService measureNaturalDateMappingService;
    @Autowired
    IUserService userService;
    @Resource
    private MetadataManager metadataManager;
    @Resource
    IWidgetDetailService widgetDetailService;
    @Resource
    IWidgetService widgetService;
    @Resource
    IDataSourceService dataSourceService;
    @Resource
    IBaseConfigureService baseConfigureService;

    @DS("mysql")
    private void loadFromDB() {
        /**
         * ******************************************************** load db start ********************************************************
         */
        List<DwTable> dwTables = dwTableMapper.selectList(null);
        Map<Integer, DwTable> dwTableMap = dwTables.stream().collect(Collectors.toMap(DwTable::getId, d -> d));
        //获取数据库相关信息
        List<MeasureApplication> measureApplications = measureApplicationMapper.selectList(Wrappers.<MeasureApplication>lambdaQuery().eq(MeasureApplication::getAvailable,YesNoType.YES.getCode()));
        List<Category> categories = categoryMapper.selectList(null);
        List<DimensionDimtableConnect> dimensionDimtableConnects = dimensionDimtableConnectMapper.selectList(null);
        List<Level> levels = levelMapper.selectList(null);
        List<Dimension> dimensions = dimensionMapper.selectList(null);
        List<DimensionFilter> dimensionFilters = dimensionFilterMapper.selectList(null);
        List<DimensionOperator> dimensionOperators = dimensionOperatorMapper.selectList(null);
        List<DimensionOperatorValue> dimensionOperatorValues = dimensionOperatorValueMapper.selectList(null);
        List<DwColumn> dwColumns = dwColumnMapper.selectList(null);
        List<Hierarchy> hierarchies = hierarchyMapper.selectList(null);
        List<MeasureNaturalDateMapping> list = measureNaturalDateMappingService.list();
        List<MeasureNaturalDateMapping> measureNaturalDateMappings = list == null ? Collections.EMPTY_LIST : list;
//        List<User> users = userService.list();
        Map<Integer, List<DwColumn>> allColumns = metadataManager.listAllColumns(dwTables);


        //结构变换
        Map<Integer, List<MeasureApplication>> measIdAppList = new HashMap<>();
        Multimap<Integer, MeasureApplication> tableIdMeasAppMap = ArrayListMultimap.create();
        Multimap<Integer, DimensionApplication> tableIdDimAppMap = ArrayListMultimap.create();
        Multimap<Integer, DimensionApplication> dimIdAppList = ArrayListMultimap.create();
        Map<Integer, MeasureApplication> measureApplicationMap = new HashMap<>();
        Map<Integer, DimensionApplication> dimensionApplicationMap = new HashMap<>();
        Map<Integer, DimensionDimtableConnect> dimIdDimtableConnectMap = dimensionDimtableConnects.stream().collect(Collectors.toMap(DimensionDimtableConnect::getDimId, d -> d));
        Map<Integer, List<Level>> hierarchyIdLevelMap = levels.stream().collect(Collectors.groupingBy(Level::getHierarchyId));
        Map<Integer, Level> dimIdLevelMap = levels.stream().collect(Collectors.toMap(Level::getDimId, l -> l));
        Map<Integer, Dimension> dimensionMap = dimensions.stream().collect(Collectors.toMap(Dimension::getId, d -> d));
        Map<String, Dimension> dimensionCodeMap = dimensions.stream().collect(Collectors.toMap(Dimension::getCode, d -> d));
        Set<Integer> dimIds = dimensionMap.keySet();
        Map<Integer, List<DimensionFilter>> measAppIdDimensionFiltersMap = dimensionFilters.stream().collect(Collectors.groupingBy(DimensionFilter::getMeasAppId));
        Map<Long, List<DimensionOperator>> filterIdOperatorsMap = dimensionOperators.stream().collect(Collectors.groupingBy(DimensionOperator::getFilterId));
        Map<Long, List<DimensionOperatorValue>> operatorIdValuesMap = dimensionOperatorValues.stream().collect(Collectors.groupingBy(DimensionOperatorValue::getOperatorId));
        Map<Integer, List<DwColumn>> tableIdColumnsMap = dwColumns.stream().collect(Collectors.groupingBy(DwColumn::getDwTableId));
        Map<Integer, Hierarchy> hierarchyMap = hierarchies.stream().collect(Collectors.toMap(Hierarchy::getId, h -> h));


        // 过滤掉不存在的维度
        measureNaturalDateMappings = measureNaturalDateMappings.stream().filter(mapping -> dimensionMap.get(mapping.getTargetDimId().intValue()) != null).collect(Collectors.toList());
        Map<Long, List<MeasureNaturalDateMapping>> measIdNaturalDateMappingMap = measureNaturalDateMappings.stream().collect(Collectors.groupingBy(MeasureNaturalDateMapping::getMeasId));
        Map<Long, List<MeasureNaturalDateMapping>> dimIdNaturalDateMappingMap = measureNaturalDateMappings.stream().collect(Collectors.groupingBy(MeasureNaturalDateMapping::getTargetDimId));
        Map<Long, List<MeasureNaturalDateMapping>> naturalDimIdNaturalDateMappingMap = measureNaturalDateMappings.stream().collect(Collectors.groupingBy(MeasureNaturalDateMapping::getNaturalDimId));
//        Map<String, User> userMap = users.stream().collect(Collectors.toMap(User::getUsername, u -> u));
//        // 添加系统用户
//        User user = new User();
//        user.setUsername("anonymous");
//        user.setNickname("系统");
//        userMap.put("anonymous", user);

        // 计算指标
        List<MeasureApplication> complexMeasureApplications = new ArrayList<>();
        // 基础指标
        List<MeasureApplication> originMeasureApplications = new ArrayList<>();
        Map<Integer, List<Filter>> measAppIdFiltersMap = new HashMap<>();
        measureApplications.forEach(ma -> {
            if (measIdAppList.get(ma.getMeasId()) == null) {
                measIdAppList.put(ma.getMeasId(), new ArrayList<MeasureApplication>());
            }
            measIdAppList.get(ma.getMeasId()).add(ma);
            tableIdMeasAppMap.put(ma.getDwTableId(), ma);
            measureApplicationMap.put(ma.getId(), ma);
            if (Objects.equals(MeasureType.DERIVED.getCode(), ma.getApplyType())) {
                complexMeasureApplications.add(ma);
            } else if (Objects.equals(MeasureType.EXTENDED.getCode(), ma.getApplyType())) {
                complexMeasureApplications.add(ma);
                // 保存派生指标的维度过滤器
                List<DimensionFilter> dimensionFilterList = measAppIdDimensionFiltersMap.get(ma.getId());
                if (!CollectionUtils.isEmpty(dimensionFilterList)) {
                    List<Filter> filterList = buildFilters(dimensionFilterList, filterIdOperatorsMap, operatorIdValuesMap);
                    measAppIdFiltersMap.put(ma.getId(), filterList);
                } else {
                    log.warn("派生指标的过滤器维空,measId:{},measAppId:{}", ma.getMeasId(), ma.getId());
                }
            } else {
                originMeasureApplications.add(ma);
            }
        });
        if (!CollectionUtils.isEmpty(dimIds)) {
            List<DimensionApplication> dimensionApplications = dimensionApplicationMapper.selectList(Wrappers.<DimensionApplication>lambdaQuery()
                    .in(DimensionApplication::getDimId, dimIds)
                    .eq(DimensionApplication::getAvailable, YesNoType.YES.getCode())
            );
            dimensionApplications.forEach(da -> {
                dimIdAppList.put(da.getDimId(), da);
                tableIdDimAppMap.put(da.getDwTableId(), da);
                dimensionApplicationMap.put(da.getId(), da);
            });
        }
        // 查询已关联事实表的指标
        List<Measure> measures = Collections.EMPTY_LIST;
        if (Objects.nonNull(measIdAppList) && !CollectionUtils.isEmpty(measIdAppList.keySet())) {
            measures = measureMapper.selectList(Wrappers.<Measure>lambdaQuery()
                    .in(Measure::getId, measIdAppList.keySet()));
        }
        Map<Integer, Measure> measureMap = measures.stream().collect(Collectors.toMap(Measure::getId, m -> m));
        Map<String, Measure> measureCodeMap = measures.stream().collect(Collectors.toMap(Measure::getCode, m -> m));
        /**
         * ******************************************************** load db end ********************************************************
         */
        // 缓存所有元信息
        // 构建指标依赖树
        List<MeasureDependencyTreeInfo> complexMeasureDependencyTrees = buildDependencyTree(measIdAppList, measureMap);
        Map<Integer, MeasureDependencyTreeInfo> complexMeasureDependencyTreeMap = complexMeasureDependencyTrees.stream().collect(Collectors.toMap(MeasureDependencyTreeInfo::getMeasId, c -> c));
        MetadataCache metadataCache = new MetadataCache();
        metadataCache.setAllMeasureMap(measureMap);
        metadataCache.setAllMeasureCodeMap(measureCodeMap);
        metadataCache.setDimensionApplicationMap(dimensionApplicationMap);
        metadataCache.setMeasureApplicationMap(measureApplicationMap);
        metadataCache.setDwTableMap(dwTableMap);
        metadataCache.setAllDimensionMap(dimensionMap);
        metadataCache.setAllDimensionCodeMap(dimensionCodeMap);
        metadataCache.setDimIdDimtableConnectMap(dimIdDimtableConnectMap);
        metadataCache.setDimIdLevelMap(dimIdLevelMap);
        metadataCache.setHierarchyIdLevelMap(hierarchyIdLevelMap);
        metadataCache.setComplexMeasureDependencyTrees(complexMeasureDependencyTrees);
        metadataCache.setMeasIdComplexMeasureDependencyTreeMap(complexMeasureDependencyTreeMap);
        metadataCache.setMeasIdAppList(measIdAppList);
        metadataCache.setMeasAppIdFiltersMap(measAppIdFiltersMap);
        metadataCache.setHierarchyMap(hierarchyMap);
        metadataCache.setSpaceContextMap(spaceManager.getSpaceContextMap());
        metadataCache.setNaturalDimIdNaturalDateMappingMap(naturalDimIdNaturalDateMappingMap);
        Map<Integer, Category> categoryMap = categories.stream().collect(Collectors.toMap(Category::getId, c -> c));
        metadataCache.setCategoryMap(categoryMap);
        buildingCache.put(CacheConstant.METADATA_CACHE_KEY, metadataCache);
        // 缓存模型信息
        cacheDwTable(dwTables, tableIdDimAppMap, tableIdMeasAppMap, dimIdLevelMap, hierarchyIdLevelMap, complexMeasureDependencyTrees, tableIdColumnsMap, measureNaturalDateMappings, allColumns);
        // 缓存基础指标信息
        cacheOriginMeasureInfo(measures, measIdAppList, tableIdDimAppMap, dimIdLevelMap, hierarchyIdLevelMap, dimensionMap, dwTableMap, measIdNaturalDateMappingMap);
        // 缓存计算指标
        cacheComplexMeasureInfo(complexMeasureApplications, dimIds, complexMeasureDependencyTreeMap, measureMap, dimensionMap, tableIdColumnsMap, dwTableMap);
        // 维度缓存
        cacheDimensionInfo(dimensions, dimIdAppList, tableIdMeasAppMap, dimIdLevelMap, hierarchyIdLevelMap, complexMeasureDependencyTrees, measureMap, dimIdNaturalDateMappingMap);
        // 缓存自然维度
        cacheNaturalDimension(measureNaturalDateMappings, complexMeasureDependencyTreeMap, dimIdLevelMap, hierarchyIdLevelMap, dimensionMap);
        // 缓存相关资源
        cacheRelateSource(dimensions, measures);

    }

    @Resource
    IDashboardService dashboardService; //896 - 947

    private void cacheRelateSource(List<Dimension> dimensions, List<Measure> measures) {
        List<Dashboard> dashboards = dashboardService.list(Wrappers.<Dashboard>lambdaQuery().eq(Dashboard::getIsDelete, YesNoType.NO.getCode()));
        Set<Long> onlineIds = dashboards.stream().map(Dashboard::getOnlineVersionId).collect(Collectors.toSet());
        Set<Long> latestIds = dashboards.stream().map(Dashboard::getLatestVersionId).collect(Collectors.toSet());
        onlineIds.addAll(latestIds);
        List<Widget> widgets = onlineIds.isEmpty() ? Collections.emptyList() :
                widgetService.list(Wrappers.<Widget>lambdaQuery().in(Widget::getDashboardVersionId, onlineIds));
        List<Long> widgetIds = widgets.stream().map(Widget::getId).collect(Collectors.toList());


        List<WidgetDetail> widgetDetails = widgetIds.isEmpty() ? Collections.emptyList() :
                widgetDetailService.list(Wrappers.<WidgetDetail>lambdaQuery().in(WidgetDetail::getWidgetId, widgetIds));
        List<DataSource> dataSources = dataSourceService.list();
        List<BaseConfigure> baseConfigures = baseConfigureService.list();

        Map<String, List<WidgetDetail>> detailMap = widgetDetails.stream().collect(Collectors.groupingBy(WidgetDetail::getCode));
        Map<Long, Widget> widgetMap = widgets.stream().collect(Collectors.toMap(Widget::getId, w -> w));
        Map<String, List<BaseConfigure>> baseConfigureMap = baseConfigures.stream().collect(Collectors.groupingBy(BaseConfigure::getCode));
        Map<Long, DataSource> dataSourceMap = dataSources.stream().collect(Collectors.toMap(DataSource::getId, d -> d));

        dimensions.forEach(d -> {
            Set<Widget> widgetSet = Optional.ofNullable(detailMap.get(d.getCode()))
                    .map(list -> list.stream().map(wd -> widgetMap.get(wd.getWidgetId())).collect(Collectors.toSet()))
                    .orElse(null);
            DimensionCache cache = getBuildingDimensionCache(d.getId());
            if (!CollectionUtils.isEmpty(widgetSet) && cache != null) {
                cache.getRelatedWidgets().addAll(widgetSet);
            }

            Set<DataSource> sourceSet = Optional.ofNullable(baseConfigureMap.get(d.getCode()))
                    .map(list -> list.stream().map(wd -> dataSourceMap.get(wd.getDataSourceId())).collect(Collectors.toSet()))
                    .orElse(null);
            if (!CollectionUtils.isEmpty(sourceSet) && cache != null) {
                cache.getRelatedDataSources().addAll(sourceSet);
            }
        });

        measures.forEach(m -> {
            Set<Widget> widgetSet = Optional.ofNullable(detailMap.get(m.getCode()))
                    .map(list -> list.stream().map(wd -> widgetMap.get(wd.getWidgetId())).collect(Collectors.toSet()))
                    .orElse(null);
            MeasureCache cache = getBuildingMeasureCache(m.getId());
            if (!CollectionUtils.isEmpty(widgetSet) && cache != null) {
                cache.getRelatedWidgets().addAll(widgetSet);
            }

            Set<DataSource> sourceSet = Optional.ofNullable(baseConfigureMap.get(m.getCode()))
                    .map(list -> list.stream().map(wd -> dataSourceMap.get(wd.getDataSourceId())).collect(Collectors.toSet()))
                    .orElse(null);
            if (!CollectionUtils.isEmpty(sourceSet) && cache != null) {
                cache.getRelatedDataSources().addAll(sourceSet);
            }
        });
    }


    private void cacheNaturalDimension(List<MeasureNaturalDateMapping> mappings, Map<Integer, MeasureDependencyTreeInfo> complexMeasureDependencyTreeMap,
                                       Map<Integer, Level> dimIdLevelMap, Map<Integer, List<Level>> hierarchyIdLevelMap, Map<Integer, Dimension> dimensionMap) {
        Map<Long, List<MeasureNaturalDateMapping>> map = mappings.stream()
                .collect(Collectors.groupingBy(MeasureNaturalDateMapping::getNaturalDimId));
        map.forEach((dimId, list) -> {
            // 1.缓存当前维度信息
            Set<Integer> relatedMeasIds = new HashSet<>();
            for (MeasureDependencyTreeInfo treeInfo : complexMeasureDependencyTreeMap.values()) {
                List<MeasureApplicationDependency> dependencyList = treeInfo.getMeasureApplicationDependencyList();
                for (MeasureApplicationDependency dependency : dependencyList) {
                    Set<Integer> measIds = list.stream().map(mapping -> mapping.getMeasId().intValue()).collect(Collectors.toSet());
                    if (!CollectionUtils.isEmpty(dependency.getDependencyBaseMeasIds()) && measIds.containsAll(dependency.getDependencyBaseMeasIds())) {
                        relatedMeasIds.add(treeInfo.getMeasId());
                    }
                }
            }
            // 2.缓存级联维度信息
            List<DimensionCache> naturalDimCaches = listBiggerDimensionWithSameHierarchyFromDB(dimId.intValue(), dimIdLevelMap, hierarchyIdLevelMap);
            for (DimensionCache naturalDimCache : naturalDimCaches) {
                naturalDimCache.getRelatedMeasureIds().addAll(relatedMeasIds);
                list.forEach(mapping -> {
                    DimensionCache cache = getBuildingDimensionCache(mapping.getTargetDimId().intValue());
                    // 自然日的维度应用表逻辑上等同于对应目标维度的应用表
                    if (cache != null) {
                        naturalDimCache.getDimensionAppIds().addAll(cache.getDimensionAppIds());
                        // naturalDimCache.getSelfAppIds().addAll(cache.getSelfAppIds());

                        naturalDimCache.getRelatedMeasureIds().addAll(list.stream().map(m -> m.getMeasId().intValue()).collect(Collectors.toList()));
                        naturalDimCache.getRelatedDwTableIds().addAll(list.stream().map(m -> m.getDwTableId().intValue()).collect(Collectors.toList()));
                    }

                });

                buildingCache.put(CacheConstant.DIMSENSION_CACHE_PREFIX + naturalDimCache.getId(), naturalDimCache);
                log.info("cache dimension info : {}", JSON.toJSONString(naturalDimCache));
            }
        });
    }

    private void cacheDwTable(List<DwTable> dwTables,
                              Multimap<Integer, DimensionApplication> tableIdDimAppMap,
                              Multimap<Integer, MeasureApplication> tableIdMeasAppMap,
                              Map<Integer, Level> dimIdLevelMap,
                              Map<Integer, List<Level>> hierarchyIdLevelMap,
                              List<MeasureDependencyTreeInfo> complexMeasureDependencyTrees,
                              Map<Integer, List<DwColumn>> tableIdColumnsMap,
                              List<MeasureNaturalDateMapping> measureNaturalDateMappings,
                              Map<Integer, List<DwColumn>> allColumns) {
        dwTables.forEach(table -> {
            DwTableCache dwTableCache = new DwTableCache();
            dwTableCache.setId(table.getId());
            Set<Integer> relatedDimIds = new HashSet<>();
            Collection<DimensionApplication> dimApplications = Optional.ofNullable(tableIdDimAppMap.get(table.getId()))
                    .orElse(Collections.EMPTY_LIST);
            Set<Integer> relatedBaseDimIds = dimApplications.stream().map(DimensionApplication::getDimId).collect(Collectors.toSet());
            // 获取相关维度(包含级联维度)
            relatedBaseDimIds.forEach(dimId -> {
                Level level = dimIdLevelMap.get(dimId);
                if (level != null) {
                    List<Level> levels = hierarchyIdLevelMap.get(level.getHierarchyId());
                    if (!CollectionUtils.isEmpty(levels)) {
                        // 获取级联维度中比当前维度粒度更大的维度(seq越小，粒度越大)
                        final Set<Integer> cascadeDimIds = levels.stream().filter(l -> l.getSequence().intValue() <= level.getSequence().intValue()).map(Level::getDimId).collect(Collectors.toSet());
                        relatedDimIds.addAll(cascadeDimIds);
                    }
                }
            });
            relatedDimIds.addAll(relatedBaseDimIds);

            // 获取相关指标(包含计算指标)
            Set<Integer> relatedMeasIds = new HashSet<>();
            Collection<MeasureApplication> measureApplications = Optional.ofNullable(tableIdMeasAppMap.get(table.getId()))
                    .orElse(Collections.EMPTY_LIST);

            Set<Integer> relatedBaseMeasIds = measureApplications.stream().map(MeasureApplication::getMeasId).collect(Collectors.toSet());
            relatedMeasIds.addAll(relatedBaseMeasIds);

            complexMeasureDependencyTrees.forEach(tree -> {
                List<MeasureApplicationDependency> measureApplicationDependencyList = tree.getMeasureApplicationDependencyList();
                measureApplicationDependencyList.forEach(mad -> {
                    Set<Integer> dependencyBaseMeasIds = mad.getDependencyBaseMeasIds();
                    Set<Integer> dependencyBaseDimIds = mad.getDependencyBaseDimIds();
                    // if (!CollectionUtils.isEmpty(dependencyBaseMeasIds) && IndicatorCollectionUtil.hasCross(relatedMeasIds, dependencyBaseMeasIds) && relatedDimIds.containsAll(dependencyBaseDimIds)) {
                    //     // 依赖的基础指标不为空 且 相关指标包含任意一个基础指标 且 相关维度包含所有基础维度
                    //     relatedMeasIds.add(tree.getMeasId());
                    // }

                    if (!CollectionUtils.isEmpty(dependencyBaseMeasIds) && relatedMeasIds.containsAll(dependencyBaseMeasIds) && relatedDimIds.containsAll(dependencyBaseDimIds)) {
                        // 依赖的基础指标不为空 且 相关指标包含任意所有基础指标 且 相关维度包含所有基础维度
                        relatedMeasIds.add(tree.getMeasId());
                    }
                });
            });
            // 获取自然维度信息
            measureNaturalDateMappings.forEach(mapping -> {
                // 如果模型相关维度关联了自然日期，那么自然日期也要加到模型的相关维度里面
                if (relatedDimIds.contains(mapping.getTargetDimId().intValue()) && relatedMeasIds.contains(mapping.getMeasId().intValue())) {
                    Level level = dimIdLevelMap.get(mapping.getNaturalDimId().intValue());
                    if (level != null) {
                        List<Level> levels = hierarchyIdLevelMap.get(level.getHierarchyId());
                        if (!CollectionUtils.isEmpty(levels)) {
                            // 获取级联维度中比当前维度粒度更大的维度(seq越小，粒度越大)
                            final Set<Integer> cascadeDimIds = levels.stream().filter(l -> l.getSequence().intValue() <= level.getSequence().intValue()).map(Level::getDimId).collect(Collectors.toSet());
                            relatedDimIds.addAll(cascadeDimIds);
                        }
                    } else {
                        if (mapping.getDwTableId().intValue() == table.getId().intValue()) {
                            relatedDimIds.add(mapping.getNaturalDimId().intValue());
                        }
                    }
                }
            });
            List<DwColumn> dwColumns = tableIdColumnsMap.get(table.getId());
            // 写入缓存
            dwTableCache.setDorisColumnList(allColumns.get(table.getId()));
            dwTableCache.setDwColumnList(dwColumns);
            dwTableCache.setRelatedDimensionIds(relatedDimIds);
            dwTableCache.setRelatedMeasureIds(relatedMeasIds);
            dwTableCache.setDwTable(table);
            buildingCache.put(CacheConstant.DWTABLE_CACHE_PREFIX + table.getId(), dwTableCache);
            log.info("cache dwTable info : {}", JSON.toJSONString(dwTableCache));
        });
    }

    @Autowired
    private DimensionQueryService dimensionQueryService;


    /**
     * 获取比当前维度粒度更小的维度(包含本身)
     *
     * @param dimId
     * @return
     */
    public List<DimensionCache> listSmallerDimensionWithSameHierarchyWithBuilding(Integer dimId) {
        Map<Integer, Level> dimIdLevelMap = getMetadataCache().getDimIdLevelMap();
        Map<Integer, List<Level>> hierarchyIdLevelMap = getMetadataCache().getHierarchyIdLevelMap();
        Level level = dimIdLevelMap.get(dimId);
        if (level != null) {
            Integer hierarchyId = level.getHierarchyId();
            List<Level> levelList = hierarchyIdLevelMap.get(hierarchyId);
            return levelList.stream()
                    .filter(l -> l.getSequence().intValue() >= level.getSequence().intValue())
                    .map(Level::getDimId)
                    .map(this::getBuildingDimensionCache)
                    .collect(Collectors.toList());
        }
        return Collections.EMPTY_LIST;
    }

    /**
     * 获取比当前维度粒度更大的维度(包含本身)
     *
     * @param dimId
     * @return
     */
    public List<DimensionCache> listBiggerDimensionWithSameHierarchy(Integer dimId) {
        Map<Integer, Level> dimIdLevelMap = getMetadataCache().getDimIdLevelMap();
        Map<Integer, List<Level>> hierarchyIdLevelMap = getMetadataCache().getHierarchyIdLevelMap();
        Level level = dimIdLevelMap.get(dimId);
        if (level != null) {
            Integer hierarchyId = level.getHierarchyId();
            List<Level> levelList = hierarchyIdLevelMap.get(hierarchyId);
            List<DimensionCache> dimensionCaches = levelList.stream()
                    .filter(l -> l.getSequence().intValue() <= level.getSequence().intValue())
                    .map(Level::getDimId)
                    .map(this::getDimensionCache)
                    .collect(Collectors.toList());
            return dimensionCaches;
        }
        List<DimensionCache> result = new ArrayList<>();
        result.add(getDimensionCache(dimId));
        return result;

    }

    /**
     * 获取比当前维度粒度更大的维度(包含本身)
     *
     * @param dimId
     * @return
     */
    public List<DimensionCache> listBiggerDimensionWithSameHierarchyWithBuilding(Integer dimId) {
        Map<Integer, Level> dimIdLevelMap = getMetadataCache().getDimIdLevelMap();
        Map<Integer, List<Level>> hierarchyIdLevelMap = getMetadataCache().getHierarchyIdLevelMap();
        Level level = dimIdLevelMap.get(dimId);
        if (level != null) {
            Integer hierarchyId = level.getHierarchyId();
            List<Level> levelList = hierarchyIdLevelMap.get(hierarchyId);
            List<DimensionCache> dimensionCaches = levelList.stream()
                    .filter(l -> l.getSequence().intValue() <= level.getSequence().intValue())
                    .map(Level::getDimId)
                    .map(this::getBuildingDimensionCache)
                    .collect(Collectors.toList());
            return dimensionCaches;
        }
        return Collections.EMPTY_LIST;

    }

    /**
     * 获取比当前维度粒度更大的维度(包含本身)
     *
     * @param dimId
     * @return
     */
    public List<DimensionCache> listBiggerDimensionWithSameHierarchyFromDB(Integer dimId, Map<Integer, Level> dimIdLevelMap, Map<Integer, List<Level>> hierarchyIdLevelMap) {
        Level level = dimIdLevelMap.get(dimId);
        if (level != null) {
            Integer hierarchyId = level.getHierarchyId();
            List<Level> levelList = hierarchyIdLevelMap.get(hierarchyId);
            List<DimensionCache> dimensionCaches = levelList.stream()
                    .filter(l -> l.getSequence().intValue() <= level.getSequence().intValue())
                    .map(Level::getDimId)
                    .map(this::getBuildingDimensionCache)
                    .filter(cache -> Objects.nonNull(cache))
                    .collect(Collectors.toList());
            return dimensionCaches;
        }
        List<DimensionCache> result = new ArrayList<>();
        DimensionCache dimensionCache = getDimensionCache(dimId);
        if (dimensionCache != null) {
            result.add(dimensionCache);
        }
        return result;

    }


    private void cacheDimensionInfo(List<Dimension> dimensions,
                                    Multimap<Integer, DimensionApplication> dimIdAppList,
                                    Multimap<Integer, MeasureApplication> tableIdMeasAppMap,
                                    Map<Integer, Level> dimIdLevelMap,
                                    Map<Integer, List<Level>> hierarchyIdLevelMap,
                                    List<MeasureDependencyTreeInfo> complexMeasureDependencyTrees,
                                    Map<Integer, Measure> measureMap,
                                    Map<Long, List<MeasureNaturalDateMapping>> dimIdNaturalDateMappingMap) {
        final List<Integer> dimIds = dimensions.stream().map(Dimension::getId).collect(Collectors.toList());
        dimensions.forEach(d -> {
            Set<Integer> relatedTableIds = new HashSet<>(); //维度相关模型
            Set<Integer> dimAppIds = new HashSet<>(); //维度相关应用
            Set<Integer> relatedMeasIds = new HashSet<>();// 维度相关指标
            Set<Integer> relatedMeasCommonDimIds = new HashSet<>();//
            Set<Integer> selfAppIds = new HashSet<>();
            relatedMeasCommonDimIds.addAll(dimIds);

            // 1.本维度相关的指标
            Collection<DimensionApplication> dimensionApplications = dimIdAppList.get(d.getId());
            if (!CollectionUtils.isEmpty(dimensionApplications)) {
                dimensionApplications.forEach(da -> {
                    Integer dwTableId = da.getDwTableId();
                    relatedTableIds.add(dwTableId);
                    dimAppIds.add(da.getId());
                    selfAppIds.add(da.getId());
                });
            }
            // // 自然日相关指标
            // List<MeasureNaturalDateMapping> measureNaturalDateMappings = dimIdNaturalDateMappingMap.get(d.getId().longValue());
            // if (! CollectionUtils.isEmpty(measureNaturalDateMappings)){
            //     Set<Integer> measIds = measureNaturalDateMappings.stream().map(mapping -> mapping.getMeasId().intValue()).collect(Collectors.toSet());
            //     relatedMeasIds.addAll(measIds);
            // }

            // 2.级联维度相关的指标
            Level level = dimIdLevelMap.get(d.getId());
            if (level != null) {
                Integer hierarchyId = level.getHierarchyId();
                List<Level> levelList = hierarchyIdLevelMap.get(hierarchyId);
                List<Level> cascadeLevel = levelList.stream().filter(l -> l.getSequence().intValue() > level.getSequence().intValue()).collect(Collectors.toList());
                cascadeLevel.forEach(l -> {
                    Collection<DimensionApplication> das = dimIdAppList.get(l.getDimId());
                    if (!CollectionUtils.isEmpty(das)) {
                        das.forEach(da -> {
                            Integer dwTableId = da.getDwTableId();
                            relatedTableIds.add(dwTableId);
                            dimAppIds.add(da.getId());
                        });
                    }
                });
            }
            relatedTableIds.forEach(tId -> {
                final Collection<MeasureApplication> list = tableIdMeasAppMap.get(tId);
                Set<Integer> collect = list.stream().map(MeasureApplication::getMeasId).collect(Collectors.toSet());
                relatedMeasIds.addAll(collect);
            });

            // 3.本维度相关的所有复合指标
            complexMeasureDependencyTrees.forEach(tree -> {
                List<MeasureApplicationDependency> measureApplicationDependencyList = tree.getMeasureApplicationDependencyList();
                measureApplicationDependencyList.forEach(mad -> {
                    Set<Integer> dependencyBaseMeasIds = mad.getDependencyBaseMeasIds();
                    Set<Integer> dependencyBaseDimIds = mad.getDependencyBaseDimIds();

                    // 新增判断:hasCommonDwTable ，新增原因：对于派生指标，依赖的维度应该与当前维度具有公有事实表，这样才能查出来数据
                    if (!CollectionUtils.isEmpty(relatedMeasIds)
                            && relatedMeasIds.containsAll(dependencyBaseMeasIds)
                            && relatedMeasCommonDimIds.containsAll(dependencyBaseDimIds)
                            && hasCommonDwTable(d.getId(), dependencyBaseDimIds)) {
                        relatedMeasIds.add(tree.getMeasId());
                    }
                });
            });

            // 写入缓存
            DimensionCache dimensionCache = new DimensionCache();
            dimensionCache.setRelatedMeasureIds(relatedMeasIds);
            List<String> relatedMeasCnNames = relatedMeasIds.stream().map(id -> measureMap.get(id)).filter(m -> m != null).map(Measure::getCnName).collect(Collectors.toList());
            dimensionCache.setRelatedMeasureCnNames(relatedMeasCnNames);
            dimensionCache.setRelatedDwTableIds(relatedTableIds);
            dimensionCache.setCode(d.getCode());
            dimensionCache.setId(d.getId());
            dimensionCache.setCnName(d.getCnName());
            dimensionCache.setDimensionAppIds(dimAppIds);
            dimensionCache.setDimension(d);
            // try {
            //     Integer dimCount = dimensionQueryService.getDimCount(d.getCode());
            //     dimensionCache.setDimValueCount(dimCount);
            // } catch (Exception e) {
            //     log.error("获取维度值数量异常,code:{}:",d.getCode(),e);
            // }
            dimensionCache.setSelfAppIds(selfAppIds);
            buildingCache.put(CacheConstant.DIMSENSION_CACHE_PREFIX + d.getId(), dimensionCache);
            log.info("cache dimension info : {}", JSON.toJSONString(dimensionCache));
        });
    }

}
