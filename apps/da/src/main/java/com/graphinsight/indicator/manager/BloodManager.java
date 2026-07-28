package com.graphinsight.indicator.manager;

import com.graphinsight.indicator.annotation.CheckCacheVersion;
import com.graphinsight.indicator.auto.entity.Dimension;
import com.graphinsight.indicator.auto.entity.DwTable;
import com.graphinsight.indicator.auto.entity.Measure;
import com.graphinsight.indicator.enums.ViewType;
import com.graphinsight.indicator.enums.YesNoType;
import com.graphinsight.indicator.exception.IndicatorParamNotValidException;
import com.graphinsight.indicator.model.cache.DimensionCache;
import com.graphinsight.indicator.model.cache.DwTableCache;
import com.graphinsight.indicator.model.cache.MeasureApplicationDependency;
import com.graphinsight.indicator.model.cache.MeasureCache;
import com.graphinsight.indicator.model.cache.MeasureDependencyTreeInfo;
import com.graphinsight.indicator.model.cache.MetadataCache;
import com.graphinsight.indicator.model.cache.SpaceContext;
import com.graphinsight.indicator.model.vo.RelatedCodeSet;
import com.graphinsight.indicator.model.vo.RelatedSet;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Date: 2022/5/16
 * Desc:
 */
@Service
@Slf4j
public class BloodManager {


    @Autowired
    CacheManager cacheManager;
    @Autowired
    SpaceManager spaceManager;


    /**
     * 获取当前空间下与measCode、dimCode能交叉的维度集合
     * @param measCode
     * @param dimCode
     * @return
     */
    public Set<Dimension> listRelatedDimensions(String measCode,String dimCode,Long spaceId){
        MetadataCache metadataCache = cacheManager.getMetadataCache();
        Map<Long, SpaceContext> spaceContextMap = metadataCache.getSpaceContextMap();
        Map<String, Measure> allMeasureCodeMap = metadataCache.getAllMeasureCodeMap();
        Map<String, Dimension> allDimensionCodeMap = metadataCache.getAllDimensionCodeMap();
        Dimension dimension = allDimensionCodeMap.get(dimCode);
        Measure measure = allMeasureCodeMap.get(measCode);
        if (Objects.isNull(dimension)){
            throw IndicatorParamNotValidException.error("维度不存在: " + dimCode);
        }

        if (Objects.isNull(measure)){
            throw IndicatorParamNotValidException.error("指标不存在:" + measCode);
        }
        // SpaceContext spaceContext = spaceContextMap.get(spaceId);
        // if (spaceContext == null){
        //     spaceContext = spaceManager.getSpaceContext(spaceId);
        // }
        // if (spaceContext == null){
        //     throw IndicatorParamNotValidException.error("空间不存在:" + spaceId);
        // }
        RelatedSet relatedSet = new RelatedSet();
        Set<Integer> dimSet = new HashSet<>();
        dimSet.add(dimension.getId());
        Set<Integer> measSet = new HashSet<>();
        measSet.add(measure.getId());
        relatedSet.setDimensionSet(dimSet);
        relatedSet.setMeasureSet(measSet);
        RelatedSet result = listRelatedSet(relatedSet);
        Map<Integer, Dimension> allDimensionMap = metadataCache.getAllDimensionMap();
        // SpaceContext finalSpaceContext = spaceContext;
        return result.getDimensionSet().stream()
                .map(id -> allDimensionMap.get(id))
                .collect(Collectors.toSet());
    }


    /**
     * 获取公共维度有日期类型的指标集合
     * @param relatedSet
     * @return
     */
    @CheckCacheVersion
    public RelatedSet listByCommonDateTypeDimension(RelatedSet relatedSet){
        RelatedSet result = listRelatedSet(relatedSet);
        Set<Integer> dimensionSet = result.getDimensionSet();
        Set<Integer> measureSet = result.getMeasureSet();
        MetadataCache metadataCache = cacheManager.getMetadataCache();
        Map<Integer, Dimension> allDimensionMap = metadataCache.getAllDimensionMap();
        Set<Integer> measIds = new HashSet<>();
        measureSet.forEach(measId -> {
            MeasureCache measureCache = cacheManager.getMeasureCache(measId);
            Set<Integer> relatedDimensionIds = measureCache.getRelatedDimensionIds();
            Set<Integer> dateTypeDimIds = relatedDimensionIds.stream()
                    .filter(id -> Objects.nonNull(allDimensionMap.get(id)) && isDate(allDimensionMap.get(id)))
                    .collect(Collectors.toSet());
            if(! CollectionUtils.isEmpty(dateTypeDimIds)){
                for (Integer dateTypeDimId : dateTypeDimIds) {
                    if (dimensionSet.contains(dateTypeDimId)){
                        measIds.add(measId);
                    }
                }
            }
        });
        result.setMeasureSet(measureSet);
        result.setDimensionSet(Collections.EMPTY_SET);
        return result;
    }




    /**
     * 获取具有日期类型的公共维度
     * @param relatedSet
     * @return
     */
    @CheckCacheVersion
    public RelatedSet listDateTypeDimension(RelatedSet relatedSet){
        RelatedSet result = listRelatedSet(relatedSet);
        if (! CollectionUtils.isEmpty(result.getDimensionSet())){
            MetadataCache metadataCache = cacheManager.getMetadataCache();
            Map<Integer, Dimension> allDimensionMap = metadataCache.getAllDimensionMap();
            Set<Integer> dateTypeDimId = result.getDimensionSet().stream()
                    .filter(id -> Objects.nonNull(allDimensionMap.get(id)) && isDate(allDimensionMap.get(id)))
                    .collect(Collectors.toSet());
            result.setDimensionSet(dateTypeDimId);
        }
        return result;
    }

    private boolean isDate(Dimension dimension){
        ViewType viewType = ViewType.findByInt(dimension.getViewType()).orElse(null);
        if (Objects.isNull(viewType)){
            return false;
        }
        switch (viewType){
            case YEAR:
            case MONTH:
            case SEASON:
            case WEEK:
            case DAY:
                return true;
            default:
                return false;
        }
    }


    public RelatedSet convert(RelatedCodeSet relatedCodeSet) {
        RelatedSet relatedSet = new RelatedSet();
        MetadataCache metadataCache = cacheManager.getMetadataCache();
        Map<String, Dimension> allDimensionCodeMap = metadataCache.getAllDimensionCodeMap();
        Map<String, Measure> allMeasureCodeMap = metadataCache.getAllMeasureCodeMap();
        Set<String> dimensionSet = relatedCodeSet.getDimensionSet();
        Set<String> measureSet = relatedCodeSet.getMeasureSet();
        Set<Integer> dimIds = dimensionSet.stream().map(code -> allDimensionCodeMap.get(code).getId()).collect(Collectors.toSet());
        Set<Integer> measIds = measureSet.stream().map(code -> allMeasureCodeMap.get(code).getId()).collect(Collectors.toSet());

        relatedSet.setMeasureSet(measIds);
        relatedSet.setDimensionSet(dimIds);
        relatedSet.setFilterWithRelyDimensions(relatedCodeSet.isFilterWithRelyDimensions());
        return relatedSet;
    }

    public RelatedCodeSet convert(RelatedSet relatedSet) {
        RelatedCodeSet result = new RelatedCodeSet();
        MetadataCache metadataCache = cacheManager.getMetadataCache();
        Map<Integer, Dimension> allDimensionMap = metadataCache.getAllDimensionMap();
        Map<Integer, Measure> allMeasureMap = metadataCache.getAllMeasureMap();
        Set<Integer> dimensionSet = relatedSet.getDimensionSet();
        Set<Integer> measureSet = relatedSet.getMeasureSet();
        Set<String> dimCodes = dimensionSet.stream().map(id -> allDimensionMap.get(id).getCode()).collect(Collectors.toSet());
        Set<String> measCodes = measureSet.stream().map(id -> allMeasureMap.get(id).getCode()).collect(Collectors.toSet());

        result.setMeasureSet(measCodes);
        result.setDimensionSet(dimCodes);
        result.setFilterWithRelyDimensions(relatedSet.isFilterWithRelyDimensions());
        return result;
    }

    /**
     * 根据selectedMset、selectedDset是否为空，分以下四种情况：
     * 1. selectedMset、selectedDset同时为空(首次进入页面)
     * 1. mset = allMset
     * 2. dset = allDset
     * 2. selectedMset为空，selectedDset不为空(只拖了维度)
     * 1. mset = {d1.dmet ∩ d2.mset ∩ d3.mset ... ∩ dn.mset}
     * 2. dset = {m1.dset ∪ m2.dset ∪ m3.dset ... ∪ mn.dset} ∩ {d1.tableSet.dset ∩ d2.tableSet.dset ∩ d3.tableSet.dset ... ∩ dn.tableSet.dset}
     * {m1,m2,m3,...,mn} = mset
     * 3. selectedMset不为空，selectedDset为空(只拖了指标)
     * 1. dset =  {m1.dset ∩ m2.dset ∩ m3.dset ... ∩ mn.dset}
     * 2. mset = {d1.dmet ∪ d2.mset ∪ d3.mset ... ∪ dn.mset}
     * {d1,d2,d3,...,dn} = dset
     * 4. selectedMset、selectedDset都不为空(既有指标又有维度)
     * 1. mset = {d1.dmet ∩ d2.mset ∩ d3.mset ... ∩ dn.mset}
     * 2. dset =  {m1.dset ∩ m2.dset ∩ m3.dse测试_100t ... ∩ mn.dset} ∩ {d1.tableSet.dset ∩ d2.tableSet.dset ∩ d3.tableSet.dset ... ∩ dn.tableSet.dset}
     *
     * @return
     */
    @CheckCacheVersion
    public RelatedSet listRelatedSet(RelatedSet relatedSet) {
        MetadataCache metadataCache = cacheManager.getMetadataCache();
        if (relatedSet.isFilterWithRelyDimensions() && !CollectionUtils.isEmpty(relatedSet.getMeasureSet())){
            // 如果用指标获取血缘关系 且 需要用指标依赖的维度作为过滤条件
            Map<Integer, MeasureDependencyTreeInfo> dependencyTreeMap = metadataCache.getMeasIdComplexMeasureDependencyTreeMap();
            relatedSet.getMeasureSet().forEach(measId -> {
                List<MeasureApplicationDependency> list = Optional.ofNullable(dependencyTreeMap.get(measId))
                        .map(tree -> tree.getMeasureApplicationDependencyList())
                        .orElse(Collections.EMPTY_LIST);
                list.forEach(mad -> {
                    relatedSet.getDimensionSet().addAll(mad.getDependencyBaseDimIds());
                });
            });

        }
        final Set<Integer> selectedMesaIds = relatedSet.getMeasureSet();
        final Set<Integer> selectedDimIds = relatedSet.getDimensionSet();
        Set<Integer> dimensionSet = new HashSet<>();
        Set<Integer> measureSet = new HashSet<>();
        final Set<Integer> allDimensionSet = metadataCache.getAllDimensionMap().keySet();
        final Set<Integer> allMeasureSet = metadataCache.getAllMeasureMap().keySet();
        if (CollectionUtils.isEmpty(relatedSet.getMeasureSet()) && CollectionUtils.isEmpty(relatedSet.getDimensionSet())) {
            // 1.所有指标和维度都可选
            measureSet.addAll(allMeasureSet);
            dimensionSet.addAll(allDimensionSet);
        } else if (CollectionUtils.isEmpty(selectedMesaIds) && !CollectionUtils.isEmpty(selectedDimIds)) {
            /**
             * 只拖了维度的情况，第一步先找到维度列表各自的相关指标，取交集，得到Mset1
             * 1.Mset1中有可能有一些指标不满足要求：因为并不能保证任意一个指标和任意一个维度能够从同一个事实表中出，因此需要从维度角度出发，找到维度的共同事实表，再根据事实表找到相关指标，取并集，得到Mset2
             * 2.Mset2可能不是全部的指标，因为有一些计算指标可能是不存在相关模型的，但是其依赖的原子指标可能是满足要求的
             * 3.Mset1 有一些指标是不合法的，Mset2指标全部合法，但是有漏掉的计算指标，所以需要第四步
             * 4.过滤Mset1，找到其中的计算指标的表达式列表，只要有一个表达式列表依赖的原子指标全部被Mset2包含，那么这个计算指标就是合法的，得到Mset3
             * 5.最终结果是Mset3 和 Mset2的并集
             */
            // 2.只拖了维度
            // 可选指标集为各个维度的可选指标集的交集
            Set<Integer> dimRelatedMeasId = new HashSet<>();
            dimRelatedMeasId.addAll(allMeasureSet);
            for (Integer dimId : selectedDimIds) {
                Set<Integer> measureIds = Optional.ofNullable(cacheManager.getDimensionCache(dimId))
                        .map(c -> c.getRelatedMeasureIds())
                        .orElse(Collections.emptySet());
                dimRelatedMeasId.retainAll(measureIds);
            }
            // 找到当前选择的所有维度的相关模型。并对各个模型的相关维度集合取交集(这一步是为了保证当前选择的维度、指标有共同的事实表)
            List<DwTable> dwTables = filterTableWithDimensionIds(selectedDimIds,Collections.EMPTY_SET, metadataCache);
            Set<Integer> dimIdsWithSameTable = new HashSet<>();
            dwTables.forEach(table -> {
                DwTableCache dwTableCache = cacheManager.getDwTableCache(table.getId());
                dimIdsWithSameTable.addAll(dwTableCache.getRelatedDimensionIds());
                measureSet.addAll(dwTableCache.getRelatedMeasureIds());
            });
            Set<Integer> complexMeasureSet = dimRelatedMeasId.stream().filter(measId -> relatedMeasContainsBaseMeasure(measId, measureSet, metadataCache)).collect(Collectors.toSet());
            measureSet.addAll(complexMeasureSet);
            // 可选维度集是可选指标集中各个指标可选维度集的并集
            for (Integer measId : measureSet) {
                Set<Integer> dimensionIds = Optional.ofNullable(cacheManager.getMeasureCache(measId))
                        .map(c -> c.getRelatedDimensionIds())
                        .orElse(Collections.emptySet());
                dimensionSet.addAll(dimensionIds);
            }
            dimensionSet.retainAll(dimIdsWithSameTable);
        } else if (!CollectionUtils.isEmpty(selectedMesaIds) && CollectionUtils.isEmpty(selectedDimIds)) {
            // 3.只拖了指标,与只拖维度相反
            dimensionSet.addAll(allDimensionSet);
            for (Integer measId : selectedMesaIds) {
                Set<Integer> dimensionIds = Optional.ofNullable(cacheManager.getMeasureCache(measId))
                        .map(c -> c.getRelatedDimensionIds())
                        .orElse(Collections.emptySet());
                dimensionSet.retainAll(dimensionIds);
            }
            for (Integer dimId : dimensionSet) {
                Set<Integer> measureIds = Optional.ofNullable(cacheManager.getDimensionCache(dimId))
                        .map(c -> c.getRelatedMeasureIds())
                        .orElse(Collections.emptySet());
                measureSet.addAll(measureIds);
            }
        } else {
            // 4.维度和指标都拖了,取指标和维度各自的可选维度、指标的交集
            dimensionSet.addAll(allDimensionSet);
            for (Integer measId : selectedMesaIds) {
                Set<Integer> dimensionIds = Optional.ofNullable(cacheManager.getMeasureCache(measId))
                        .map(c -> c.getRelatedDimensionIds())
                        .orElse(Collections.emptySet());
                dimensionSet.retainAll(dimensionIds);
            }
            measureSet.addAll(allMeasureSet);
            for (Integer dimId : selectedDimIds) {
                Set<Integer> measureIds = Optional.ofNullable(cacheManager.getDimensionCache(dimId))
                        .map(c -> c.getRelatedMeasureIds())
                        .orElse(Collections.emptySet());
                measureSet.retainAll(measureIds);
            }
            // 找到当前选择的所有维度的相关模型。并对各个模型的相关维度集合取交集(这一步是为了保证当前选择的维度、指标有共同的事实表)
            List<DwTable> dwTables = filterTableWithDimensionIds(selectedDimIds,selectedMesaIds, metadataCache);
            Set<Integer> dimIdsWithSameTable = new HashSet<>();
            dwTables.forEach(table -> {
                DwTableCache dwTableCache = cacheManager.getDwTableCache(table.getId());
                dimIdsWithSameTable.addAll(dwTableCache.getRelatedDimensionIds());

            });
            dimensionSet.retainAll(dimIdsWithSameTable);
        }
        RelatedSet result = new RelatedSet();
        // 去除传进来的id
        // dimensionSet.removeAll(selectedDimIds);
        // measureSet.removeAll(selectedMesaIds);


        // 返回结果
        result.setDimensionSet(dimensionSet);
        Set<Integer> tempMeasureSet = new HashSet<>();
        /**
         * 计算指标可能由多个派生指标嵌套生成
         * 在拖拽查询页面，计算指标只需要跟其他指标有交叉即可
         * 但是在配置计算指标时，指标的相关维度需要完全包含一个派生指标依赖的维度集合
         * 比如 派生指标m1,依赖的维度有 d1,d2
         * 能够与m1配置生成复合指标的指标，其相关维度必须包含d1,d2 也就相当于在拖拽页面既拖了m1 又拖了d1、d2
         */
        if (relatedSet.isFilterWithRelyDimensions() && !CollectionUtils.isEmpty(measureSet)){
            // 如果用指标获取血缘关系 且 需要用指标依赖的维度作为过滤条件
            Map<Integer, MeasureDependencyTreeInfo> dependencyTreeMap = cacheManager.getMetadataCache().getMeasIdComplexMeasureDependencyTreeMap();
            measureSet.forEach(measId -> {
                List<MeasureApplicationDependency> list = Optional.ofNullable(dependencyTreeMap.get(measId))
                        .map(tree -> tree.getMeasureApplicationDependencyList())
                        .orElse(Collections.EMPTY_LIST);
                Set<Integer> dimSet = new HashSet<>();
                list.forEach(mad -> {
                    dimSet.addAll(mad.getDependencyBaseDimIds());
                });
                if (dimensionSet.containsAll(dimSet)){
                    tempMeasureSet.add(measId);
                }
            });
        } else {
            tempMeasureSet.addAll(measureSet);
        }
        Set<Integer> resultMeasureSet = filterMeasureWithoutFactTable(tempMeasureSet);
        filterNaturalDimensionMeasure(selectedDimIds,resultMeasureSet);
        result.setMeasureSet(resultMeasureSet);
        result.setFilterWithRelyDimensions(relatedSet.isFilterWithRelyDimensions());
        return result;
    }

    /**
     * 如果是自然维度，不能根据表关系去判断是否有血缘，只能根据自然维度和指标本身的关联，比如：
     * 订单表有 订单量、下单时间、退单量、退单时间，用自然日关联下单时间，此时自然日跟订单量有血缘，但是自然日是不能查退单量的，否则会有语义问题
     * @param dimIds
     * @param measIds
     */
    private void filterNaturalDimensionMeasure(Collection<Integer> dimIds, Collection<Integer> measIds){
        if (! CollectionUtils.isEmpty(dimIds)){
            dimIds.forEach( id -> {
                DimensionCache dimensionCache = cacheManager.getDimensionCache(id);
                Dimension dimension = dimensionCache.getDimension();
                if (Objects.equals(dimension.getIsHyper(), YesNoType.YES.getCode())){
                    // 自然维度
                    measIds.retainAll(dimensionCache.getRelatedMeasureIds());
                }
            });
        }
    }

    /**
     * 过滤掉没有事实表的指标
     * @param measIds
     */
    private Set<Integer> filterMeasureWithoutFactTable(Set<Integer> measIds ){
        if (!CollectionUtils.isEmpty(measIds)){
            return measIds.stream().filter(id -> hasFactTable(id)).collect(Collectors.toSet());
        }
        return Collections.EMPTY_SET;
    }

    public boolean hasFactTable(Integer measId){
        MeasureCache measureCache = cacheManager.getMeasureCache(measId);
        if (measureCache != null){
            return !CollectionUtils.isEmpty(measureCache.getMeasureApplicationCacheList());
        }
        return false;
    }


    private List<DwTable> filterTableWithDimensionIds(Set<Integer> dimIds,Set<Integer> measIds,MetadataCache metadataCache){
        Map<Integer, DwTable> dwTableMap = metadataCache.getDwTableMap();
        List<DwTable> dwTables = dwTableMap.values().stream().filter(table -> {
            DwTableCache dwTableCache = cacheManager.getDwTableCache(table.getId());
            Set<Integer> relatedDimensionIds = dwTableCache.getRelatedDimensionIds();
            Set<Integer> relatedMeasureIds = dwTableCache.getRelatedMeasureIds();
            if (relatedDimensionIds.containsAll(dimIds) && tableRelatedMeasIdsContainsBaseMeasure(relatedMeasureIds,measIds, metadataCache)) {
                return true;
            } else {
                return false;
            }
        }).collect(Collectors.toList());
        return dwTables;
    }

    private boolean tableRelatedMeasIdsContainsBaseMeasure(Set<Integer> relatedMeasureIds, Set<Integer> measIds,MetadataCache metadataCache){
        if (CollectionUtils.isEmpty(measIds)){
            return true;
        }
        if (CollectionUtils.isEmpty(relatedMeasureIds)){
            return false;
        }
        Map<Integer, MeasureDependencyTreeInfo> measIdComplexMeasureDependencyTreeMap = metadataCache.getMeasIdComplexMeasureDependencyTreeMap();
        Set<Integer> realMeasIds = new HashSet<>();
        realMeasIds.addAll(measIds);
        measIds.forEach(measId -> {
            MeasureDependencyTreeInfo treeInfo = measIdComplexMeasureDependencyTreeMap.get(measId);
            if (Objects.nonNull(treeInfo) && CollectionUtils.isEmpty(treeInfo.getMeasureApplicationDependencyList())){
                List<MeasureApplicationDependency> measureApplicationDependencyList = treeInfo.getMeasureApplicationDependencyList();
                measureApplicationDependencyList.forEach(mad -> {
                    realMeasIds.addAll(mad.getDependencyBaseMeasIds());
                });
            }
        });
        return relatedMeasureIds.stream().anyMatch(item -> realMeasIds.contains(item));
    }

    /**
     * 判断复合指标依赖的表达式中，是否有表达式依赖的基础指标全部包含在某个集合内，只要有一个表达式满足，就返回true
     * @param measId
     * @param measureSet
     * @return
     */
    private boolean relatedMeasContainsBaseMeasure(Integer measId, Set<Integer> measureSet,MetadataCache metadataCache){
        if (measId == null || CollectionUtils.isEmpty(measureSet)){
            return false;
        }
        Map<Integer, MeasureDependencyTreeInfo> measIdComplexMeasureDependencyTreeMap = metadataCache.getMeasIdComplexMeasureDependencyTreeMap();
        MeasureDependencyTreeInfo treeInfo = measIdComplexMeasureDependencyTreeMap.get(measId);
        if (Objects.nonNull(treeInfo) && !CollectionUtils.isEmpty(treeInfo.getMeasureApplicationDependencyList())){
            List<MeasureApplicationDependency> measureApplicationDependencyList = treeInfo.getMeasureApplicationDependencyList();
            for (MeasureApplicationDependency mad : measureApplicationDependencyList) {
                return measureSet.containsAll(mad.getDependencyBaseMeasIds());
            }
        }
        return false;
    }

}
