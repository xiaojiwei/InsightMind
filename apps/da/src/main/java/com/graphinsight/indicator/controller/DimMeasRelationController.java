package com.graphinsight.indicator.controller;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.google.common.collect.ArrayListMultimap;
import com.graphinsight.indicator.annotation.CheckCacheVersion;
import com.graphinsight.indicator.auto.entity.*;
import com.graphinsight.indicator.auto.mapper.DimensionMapper;
import com.graphinsight.indicator.auto.mapper.MeasureMapper;
import com.graphinsight.indicator.auto.mapper.SourceMapper;
import com.graphinsight.indicator.auto.service.*;
import com.graphinsight.indicator.constant.IndicatorConstant;
import com.graphinsight.indicator.enums.DecisionTreeNodeType;
import com.graphinsight.indicator.exception.IndicatorParamNotValidException;
import com.graphinsight.indicator.manager.BloodManager;
import com.graphinsight.indicator.manager.CacheManager;
import com.graphinsight.indicator.manager.CategoryManager;
import com.graphinsight.indicator.manager.MeasureManager;
import com.graphinsight.indicator.manager.SpaceManager;
import com.graphinsight.indicator.manager.UserManager;
import com.graphinsight.indicator.model.Response;
import com.graphinsight.indicator.model.cache.DimensionCache;
import com.graphinsight.indicator.model.cache.MetadataCache;
import com.graphinsight.indicator.model.cache.SpaceContext;
import com.graphinsight.indicator.model.dto.DismantlingData;
import com.graphinsight.indicator.model.dto.UserContext;
import com.graphinsight.indicator.model.vo.BaseInfo;
import com.graphinsight.indicator.model.vo.BatchRelatedCodeSet;
import com.graphinsight.indicator.model.vo.CategoryNodeItem;
import com.graphinsight.indicator.model.vo.CategoryQueryVO;
import com.graphinsight.indicator.model.vo.CategoryTree;
import com.graphinsight.indicator.model.vo.CategoryTreeNode;
import com.graphinsight.indicator.model.vo.DimensionBaseVO;
import com.graphinsight.indicator.model.vo.MeasureDrillDown;
import com.graphinsight.indicator.model.vo.MeasureQueryParam;
import com.graphinsight.indicator.model.vo.QueryBaseInfoVO;
import com.graphinsight.indicator.model.vo.RelatedCodeSet;
import com.graphinsight.indicator.model.vo.RelatedSet;
import com.graphinsight.indicator.util.UserThreadLocalUtil;
import io.swagger.annotations.ApiOperation;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.util.CollectionUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

/**
 * @Description: 指标维度关联
 * @Date: 2021/11/23
 */
@Slf4j
@RestController
@RequestMapping("/relation")
public class DimMeasRelationController {

    @Value("${meas.fei_yong_code:MEAS_b5159de555304d21b7869ac3c834b380}")
    private String feiYongCode;
    @Autowired
    CacheManager cacheManager;
    @Autowired
    MeasureManager measureManager;
    @Autowired
    CategoryManager categoryManager;
    @Autowired
    BloodManager bloodManager;
    @Autowired
    ITSuperAdminService superAdminService;
    @Autowired
    IDecisionTreeService decisionTreeService;
    @Autowired
    IDecisionTreeDetailService decisionTreeDetailService;
    @Autowired
    private UserManager userManager;

    @CheckCacheVersion
    @ApiOperation("获取分类树")
    @PostMapping("/space/category/tree")
    public Response<List<CategoryTree>> listTree(@RequestBody CategoryQueryVO categoryQueryVO) {
        if (categoryQueryVO.getSpaceId() == null) {
            return Response.ok(Collections.EMPTY_LIST);
        }
        List<CategoryTree> treeList = categoryManager.getTreeBySpaceId(categoryQueryVO.getSpaceId(), categoryQueryVO.getCurrentSpace());
        // List<CategoryTreeNode<CategoryNodeItem>> categoryTreeNodesBySpace = categoryManager.getCategoryTreeNodesBySpace(categoryQueryVO);
        return Response.ok(treeList);
    }

    @CheckCacheVersion
    @ApiOperation("根据分类查询指标")
    @PostMapping("/space/category/measure/tree")
    public Response<List<CategoryNodeItem>> listMeasureByCategoryId(@RequestBody MeasureQueryParam query) {
        Set<Integer> categoryIds = query.getCategoryIds();
        if (CollectionUtils.isEmpty(categoryIds)) {
            return Response.ok(Collections.EMPTY_LIST);
        }
        MetadataCache metadataCache = cacheManager.getMetadataCache();
        Map<Integer, Measure> allMeasureMap = metadataCache.getAllMeasureMap();
        Set<Integer> authMeasureIds = new HashSet<>();
        Set<Integer> belongSpaceMeasIds = allMeasureMap.keySet();
        String username = UserThreadLocalUtil.getUserName();
        List<TSuperAdmin> superAdmins = superAdminService.list(Wrappers.<TSuperAdmin>lambdaQuery().eq(TSuperAdmin::getEmpCode, username));
        if (Objects.isNull(query.getSpaceId()) || !CollectionUtils.isEmpty(superAdmins)) {
            // 不传空间id或者是超级管理员，则拥有所有指标权限
            authMeasureIds = allMeasureMap.keySet();
        } else {
            UserContext userContext = userManager.getUserContext(query.getSpaceId(), username);
            authMeasureIds = userContext.getAuthMeasures().stream().filter(Objects::nonNull).map(Measure::getId).collect(Collectors.toSet());
        }

        if (Objects.nonNull(query.getSpaceId())) {
            SpaceContext spaceContext = spaceManager.getSpaceContext(query.getSpaceId());
            belongSpaceMeasIds = spaceContext.getMeasIdsWithChildren();
        }
        Set<Integer> children = categoryManager.findChildrenFromCache(categoryIds);
        Set<Integer> finalBelongSpaceMeasIds = belongSpaceMeasIds;
        Set<Integer> finalAuthMeasureIds = authMeasureIds;
        List<CategoryNodeItem> result = allMeasureMap.values().stream()
                .filter(measure -> children.contains(measure.getLeafCategoryId()))
                .filter(measure -> bloodManager.hasFactTable(measure.getId())).map(m -> {
                    CategoryNodeItem item = new CategoryNodeItem();
                    BeanUtils.copyProperties(m, item);
                    item.setType("measure");
                    if (null != m.getFunctionType()) {
                        switch (m.getFunctionType()) {
                            case "CPD":
                                item.setExpression("CDP([" + feiYongCode + "])");
                                break;
                            case "ER":
                                item.setExpression("ER([" + feiYongCode + "])");
                                break;
                        }
                    }
                    item.setHasAuth(finalAuthMeasureIds.contains(m.getId()));
                    item.setBelongSpace(finalBelongSpaceMeasIds.contains(m.getId()));
                    item.setOnline(m.getOnline());
                    String offlineReason = "当前指标暂不可查，原因" + m.getOfflineOperator() + "在" + m.getOfflineTime().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")) + "将指标下线，下线原因：" + m.getOfflineRemark();
                    item.setOfflineReason(offlineReason);
                    return item;
                }).collect(Collectors.toList());
        return Response.ok(result);
    }

    @CheckCacheVersion
    @ApiOperation("根据分类查询维度")
    @PostMapping("/space/category/dimension/tree")
    public Response<List<DimensionBaseVO>> listDimensionByCategoryId(@RequestBody MeasureQueryParam query) {
        MetadataCache metdataCache = cacheManager.getMetadataCache();
        Map<Integer, Dimension> allDimensionMap = metdataCache.getAllDimensionMap();
        Map<String, DimensionBaseVO> baseVOMap = convert(allDimensionMap, metdataCache);
        return Response.ok(baseVOMap.values());
    }


    @CheckCacheVersion
    @ApiOperation("指标是否能下钻接口")
    @PostMapping("/drill/down")
    public Response<List<MeasureDrillDown>> measureDrillDown(@RequestBody RelatedCodeSet relatedCodeSet) {
        Set<String> measureSet = relatedCodeSet.getMeasureSet();
        if (CollectionUtils.isEmpty(measureSet)) {
            return Response.ok(Collections.EMPTY_LIST);
        }
        List<MeasureDrillDown> drillDowns = measureSet.stream().map(mCode -> {
            MeasureDrillDown measureDrillDown = new MeasureDrillDown();
            measureDrillDown.setCode(mCode);
            measureDrillDown.setDrillDown(measureManager.canDrillDown(mCode, relatedCodeSet.getDimensionSet()));
            return measureDrillDown;
        }).collect(Collectors.toList());
        return Response.ok(drillDowns);
    }


    @CheckCacheVersion
    @ApiOperation("获取决策树的可选日期型维度列表")
    @GetMapping("/list/dimension/byTreeId/{treeId}")
    public Response<List<BaseInfo>> listDimensionByTreeId(@PathVariable("treeId") Long treeId) {
        DecisionTree decisionTree = decisionTreeService.getById(treeId);
        if (Objects.isNull(decisionTree)) {
            throw IndicatorParamNotValidException.error("决策树不存在,ID:" + treeId);
        }
        List<DecisionTreeDetail> decisionTreeDetails = decisionTreeDetailService.list(Wrappers.<DecisionTreeDetail>lambdaQuery()
                .eq(DecisionTreeDetail::getTreeId, treeId));
        if (CollectionUtils.isEmpty(decisionTreeDetails)) {
            return Response.ok(Collections.EMPTY_LIST);
        }
        Set<String> measCodes = decisionTreeDetails.stream()
                .filter(decisionTreeDetail -> Objects.equals(DecisionTreeNodeType.MEASURE.getCode(), decisionTreeDetail.getNodeType()))
                .map(DecisionTreeDetail::getNodeValue).collect(Collectors.toSet());
        RelatedCodeSet relatedCodeSet = new RelatedCodeSet();
        relatedCodeSet.setMeasureSet(measCodes);
        RelatedSet convert = convert(relatedCodeSet);
        RelatedSet relatedSet = bloodManager.listDateTypeDimension(convert);
        return Response.ok(convertDimension2BaseInfo(relatedSet));
    }

    @CheckCacheVersion
    @ApiOperation("获取日期类型的公共维度")
    @PostMapping("/list/dateTypeDimension/")
    public Response<List<BaseInfo>> listDimensionByMeasCode(@RequestBody RelatedCodeSet relatedCodeSet) {
        RelatedSet relatedSet = bloodManager.listDateTypeDimension(convert(relatedCodeSet));
        return Response.ok(convertDimension2BaseInfo(relatedSet));
    }

    private List<BaseInfo> convertDimension2BaseInfo(RelatedSet relatedSet) {
        Map<Integer, Dimension> allDimensionMap = cacheManager.getMetadataCache().getAllDimensionMap();
        List<BaseInfo> infos = relatedSet.getDimensionSet().stream()
                .map(id -> {
                    BaseInfo baseInfo = new BaseInfo();
                    Dimension dimension = allDimensionMap.get(id);
                    BeanUtils.copyProperties(dimension, baseInfo);
                    return baseInfo;
                }).collect(Collectors.toList());
        return infos;
    }

    @CheckCacheVersion
    @ApiOperation("获取具有日期类型公共维度的指标集合")
    @PostMapping("/list/measure/byDateTypeDimension")
    public Response<List<BaseInfo>> listDateTypeDimensionRelatedSet(@RequestBody RelatedCodeSet relatedCodeSet) {
        RelatedSet relatedSet = convert(relatedCodeSet);
        String userName = UserThreadLocalUtil.getUserName();
        UserContext userContext = userManager.getUserContext(relatedCodeSet.getSpaceId(), userName);
        Map<Long, SpaceContext> spaceContextMap = cacheManager.getMetadataCache().getSpaceContextMap();
        SpaceContext spaceContext = spaceContextMap.get(relatedCodeSet.getSpaceId());
        if (Objects.isNull(userContext) || Objects.isNull(spaceContext)) {
            return Response.ok(Collections.EMPTY_LIST);
        }
        RelatedSet resultIdSet = bloodManager.listByCommonDateTypeDimension(relatedSet);
        Map<Integer, Measure> allMeasureMap = cacheManager.getMetadataCache().getAllMeasureMap();
        Set<Integer> measureSet = resultIdSet.getMeasureSet();
        Set<Integer> userAuthMeasureCodes = userContext.getAuthMeasures().stream().filter(Objects::nonNull).map(Measure::getId).collect(Collectors.toSet());
        Set<Integer> measIdsWithChildren = spaceContext.getMeasIdsWithChildren();
        List<BaseInfo> measInfos = measureSet.stream()
                .filter(id -> userAuthMeasureCodes.contains(id) && measIdsWithChildren.contains(id))
                .map(id -> {
                    BaseInfo baseInfo = new BaseInfo();
                    Measure measure = allMeasureMap.get(id);
                    BeanUtils.copyProperties(measure, baseInfo);
                    return baseInfo;
                }).collect(Collectors.toList());
        return Response.ok(measInfos);
    }


    @Autowired
    ITSpaceService itSpaceService;

    @CheckCacheVersion
    @PostMapping("/listByCode")
    public Response<RelatedCodeSet> listRelatedSet(@RequestBody RelatedCodeSet relatedCodeSet) {
        RelatedSet relatedSet = convert(relatedCodeSet);
        RelatedSet resultRelatedSet = bloodManager.listRelatedSet(relatedSet);

        UserContext userContext = userManager.getUserContext(itSpaceService.getAiSpaceById().getId(), UserThreadLocalUtil.getUserName());
        Set<Integer> authMeasIds = userContext.getAuthMeasures().stream().filter(Objects::nonNull).map(com.graphinsight.indicator.auto.entity.Measure::getId).collect(Collectors.toSet());

        Iterator<Integer> iterator = resultRelatedSet.getMeasureSet().iterator();
        while (iterator.hasNext()) {
            Integer element = iterator.next();
            if (!authMeasIds.contains(element)) {
                iterator.remove();  // 使用迭代器进行删除操作
            }
        }

        RelatedCodeSet result = convert(resultRelatedSet);
        return Response.ok(result);
    }

    public RelatedCodeSet listRelatedSetDemo(RelatedCodeSet relatedCodeSet) {
        try {
            RelatedSet relatedSet = convert(relatedCodeSet);
            RelatedSet resultRelatedSet = bloodManager.listRelatedSet(relatedSet);
            RelatedCodeSet result = convert(resultRelatedSet);
            return result;
        } catch (Exception e) {
            log.info("error infos is {}", e);
            return null;
        }

    }

    @CheckCacheVersion
    @PostMapping("/batch/listByCode")
    public Response<RelatedCodeSet> bathListRelatedSet(@RequestBody BatchRelatedCodeSet batchRelatedCodeSet) {
        MetadataCache metadataCache = cacheManager.getMetadataCache();
        Map<String, Dimension> allDimensionCodeMap = metadataCache.getAllDimensionCodeMap();
        Map<String, Measure> allMeasureCodeMap = metadataCache.getAllMeasureCodeMap();
        Set<String> dimensionCodeSet = allDimensionCodeMap.keySet();
        Set<String> measureCodeSet = allMeasureCodeMap.keySet();
        Set<String> dimCodes = new HashSet<>();
        Set<String> measCodes = new HashSet<>();
        dimCodes.addAll(dimensionCodeSet);
        measCodes.addAll(measureCodeSet);
        batchRelatedCodeSet.getRelatedCodeSetList().forEach(relatedCodeSet -> {
            RelatedSet relatedSet = convert(relatedCodeSet);
            RelatedSet resultRelatedSet = bloodManager.listRelatedSet(relatedSet);
            RelatedCodeSet result = convert(resultRelatedSet);
            dimCodes.retainAll(result.getDimensionSet());
            measCodes.retainAll(result.getMeasureSet());
        });
        RelatedCodeSet relatedCodeSet = new RelatedCodeSet();
        relatedCodeSet.setDimensionSet(dimCodes);
        relatedCodeSet.setMeasureSet(measCodes);
        return Response.ok(relatedCodeSet);
    }

    private RelatedSet convert(RelatedCodeSet relatedCodeSet) {
        RelatedSet relatedSet = new RelatedSet();
        MetadataCache metadataCache = cacheManager.getMetadataCache();
        Map<Integer, Measure> allMeasureMap = metadataCache.getAllMeasureMap();
        Map<Integer, Dimension> allDimensionMap = metadataCache.getAllDimensionMap();
        Map<String, List<Dimension>> dimensionMap = allDimensionMap.values().stream().collect(Collectors.groupingBy(Dimension::getCode));
        Map<String, List<Measure>> measureMap = allMeasureMap.values().stream().collect(Collectors.groupingBy(Measure::getCode));
        Set<String> dimensionSet = relatedCodeSet.getDimensionSet();
        Set<String> measureSet = relatedCodeSet.getMeasureSet();
        Set<Integer> dimIds = dimensionSet.stream().map(code -> dimensionMap.get(code).get(0).getId()).collect(Collectors.toSet());
        Set<Integer> measIds = measureSet.stream().map(code -> measureMap.get(code).get(0).getId()).collect(Collectors.toSet());

        relatedSet.setMeasureSet(measIds);
        relatedSet.setDimensionSet(dimIds);
        relatedSet.setFilterWithRelyDimensions(relatedCodeSet.isFilterWithRelyDimensions());
        return relatedSet;
    }

    private RelatedCodeSet convert(RelatedSet relatedSet) {
        RelatedCodeSet result = new RelatedCodeSet();
        MetadataCache metadataCache = cacheManager.getMetadataCache();
        Map<String, Dimension> allDimensionCodeMap = metadataCache.getAllDimensionCodeMap();
        Map<Integer, Dimension> allDimensionMap = metadataCache.getAllDimensionMap();
        Map<Integer, Measure> allMeasureMap = metadataCache.getAllMeasureMap();
        Set<Integer> dimensionSet = relatedSet.getDimensionSet();
        Set<Integer> measureSet = relatedSet.getMeasureSet();
        Set<String> dimCodes = dimensionSet.stream().map(id -> allDimensionMap.get(id)).filter(d -> d != null).map(d -> d.getCode()).collect(Collectors.toSet());
        Set<String> measCodes = measureSet.stream().map(id -> allMeasureMap.get(id)).filter(m -> m != null).map(m -> m.getCode()).collect(Collectors.toSet());

        result.setMeasureSet(measCodes);
        result.setDimensionSet(dimCodes);
        result.setFilterWithRelyDimensions(relatedSet.isFilterWithRelyDimensions());
        return result;
    }

    @CheckCacheVersion
    @PostMapping("/list")
    public Response<RelatedSet> listRelatedSet(@RequestBody RelatedSet relatedSet) {
        RelatedSet result = bloodManager.listRelatedSet(relatedSet);
        return Response.ok(result);
    }


    @Autowired
    private SpaceManager spaceManager;


    @CheckCacheVersion
    @GetMapping("/tree/measure")
    public Response<List<CategoryTreeNode<CategoryNodeItem>>> treeAllMeasure(@RequestParam(value = "traceId", required = false) String traceId,
                                                                             @RequestParam(value = "spaceId", required = false) Long spaceId) {
        MetadataCache metdataCache = cacheManager.getMetadataCache();
        Map<Integer, Measure> allMeasureMap = metdataCache.getAllMeasureMap();
        String username = UserThreadLocalUtil.getUserName();
        UserContext userContext = userManager.getUserContext(spaceId, username);
        Set<Integer> authMeasureIds;
        Set<Integer> belongSpaceMeasIds = allMeasureMap.keySet();
        List<TSuperAdmin> superAdmins = superAdminService.list(Wrappers.<TSuperAdmin>lambdaQuery().eq(TSuperAdmin::getEmpCode, username));
        if (Objects.isNull(spaceId) || !CollectionUtils.isEmpty(superAdmins)) {
            // 不传空间id或者是超级管理员，则拥有所有指标权限
            authMeasureIds = allMeasureMap.keySet();
        } else {
            authMeasureIds = userContext.getAuthMeasures().stream().filter(Objects::nonNull).map(Measure::getId).collect(Collectors.toSet());
        }

        if (Objects.nonNull(spaceId)) {
            SpaceContext spaceContext = spaceManager.getSpaceContext(spaceId);
            belongSpaceMeasIds = spaceContext.getMeasIdsWithChildren();
        }

        Set<Integer> finalBelongSpaceMeasIds = belongSpaceMeasIds;
        List<CategoryTreeNode<CategoryNodeItem>> baseInfoList = allMeasureMap.values().stream().filter(measure -> bloodManager.hasFactTable(measure.getId())).map(m -> {
            CategoryTreeNode<CategoryNodeItem> baseInfo = new CategoryTreeNode();
            CategoryNodeItem item = new CategoryNodeItem();
            BeanUtils.copyProperties(m, item);
            item.setType("measure");
            item.setHasAuth(authMeasureIds.contains(m.getId()));
            item.setBelongSpace(finalBelongSpaceMeasIds.contains(m.getId()));
            baseInfo.setData(item);
            return baseInfo;
        }).collect(Collectors.toList());

        ArrayListMultimap<Integer, CategoryTreeNode<CategoryNodeItem>> multimap = ArrayListMultimap.create();
        List<CategoryTreeNode<CategoryNodeItem>> measureWithoutCategoryList = new ArrayList<>();
        baseInfoList.forEach(b -> {
            if (b.getData().getLeafCategoryId() == null) {
                measureWithoutCategoryList.add(b);
            } else {
                CategoryNodeItem data = new CategoryNodeItem();
                BeanUtils.copyProperties(b, data);
                multimap.put(b.getData().getLeafCategoryId(), b);
            }
        });


        List<CategoryTreeNode<CategoryNodeItem>> categoryTreeList = categoryManager.getCategoryTreeNodes(CategoryQueryVO.builder().meas(true).spaceId(spaceId).build(), metdataCache);
        setData(multimap, categoryTreeList, Collections.EMPTY_SET);
        if (!CollectionUtils.isEmpty(measureWithoutCategoryList)) {
            CategoryTreeNode<CategoryNodeItem> measureWithoutCategoryTree = new CategoryTreeNode();
            CategoryNodeItem item = new CategoryNodeItem();
            item.setCnName("未分类");
            item.setType("category");
            item.setId(IndicatorConstant.UNCATEGORIZED_ID);
            measureWithoutCategoryTree.setChildren(measureWithoutCategoryList);
            measureWithoutCategoryTree.setData(item);
            categoryTreeList.add(measureWithoutCategoryTree);
        }
        return Response.ok(categoryTreeList);
    }


    @CheckCacheVersion
    @GetMapping("/list/dimension")
    public Response<List<BaseInfo>> listAllDimension(@RequestParam(value = "traceId", required = false) String traceId) {
        MetadataCache metdataCache = cacheManager.getMetadataCache();
        Map<Integer, Dimension> allDimensionMap = metdataCache.getAllDimensionMap();
        List<BaseInfo> baseInfoList = allDimensionMap.values().stream().map(d -> {
            BaseInfo baseInfo = new BaseInfo();
            BeanUtils.copyProperties(d, baseInfo);
            return baseInfo;
        }).collect(Collectors.toList());
        return Response.ok(baseInfoList);
    }

    @CheckCacheVersion
    @GetMapping("/space/tree/dimension")
    public Response<List<CategoryTreeNode<CategoryNodeItem>>> spaceTreeAllDimension(@RequestParam(value = "traceId", required = false) String traceId) {
        MetadataCache metdataCache = cacheManager.getMetadataCache();
        Map<Integer, List<Level>> hierarchyIdLevelMap = metdataCache.getHierarchyIdLevelMap();
        Map<Integer, Dimension> allDimensionMap = metdataCache.getAllDimensionMap();
        Set<Integer> cascadeDimIds = new HashSet<>();
        Set<Integer> ignoreIds = new HashSet<>();
        List<CategoryTreeNode<CategoryNodeItem>> dataList = new ArrayList<>();
        if (!CollectionUtils.isEmpty(hierarchyIdLevelMap)) {
            hierarchyIdLevelMap.forEach((h, leves) -> {
                List<Level> levelList = leves.stream().sorted(Comparator.comparing(Level::getSequence)).collect(Collectors.toList());
                Level level = levelList.get(0);
                Integer dimId = level.getDimId();
                ignoreIds.add(dimId);
                Dimension dimension = allDimensionMap.get(dimId);
                DimensionCache dimensionCache = cacheManager.getDimensionCache(dimension.getId());
                CategoryTreeNode<CategoryNodeItem> dimensionBaseVO = new CategoryTreeNode<>();
                CategoryNodeItem data = new CategoryNodeItem();
                BeanUtils.copyProperties(dimension, data);
                data.setDimValueCount(dimensionCache.getDimValueCount());
                data.setType("dimension");
                dimensionBaseVO.setData(data);
                List<CategoryTreeNode<CategoryNodeItem>> cascadeDimensions = new ArrayList<>();
                cascadeDimIds.add(dimId);
                for (int i = 1; i < levelList.size(); i++) {
                    CategoryTreeNode<CategoryNodeItem> cascadeDimensionBaseVO = new CategoryTreeNode<>();
                    Dimension d = allDimensionMap.get(levelList.get(i).getDimId());
                    if (Objects.nonNull(d)) {
                        CategoryNodeItem item = new CategoryNodeItem();
                        BeanUtils.copyProperties(d, item);
                        item.setType("dimension");
                        DimensionCache dc = cacheManager.getDimensionCache(d.getId());
                        item.setDimValueCount(dc.getDimValueCount());
                        cascadeDimensionBaseVO.setData(item);
                        cascadeDimensions.add(cascadeDimensionBaseVO);
                        cascadeDimIds.add(d.getId());
                    }
                }
                dimensionBaseVO.setChildren(cascadeDimensions);
                dataList.add(dimensionBaseVO);
            });
        }
        Set<Integer> allDimIds = new HashSet<>();
        allDimIds.addAll(allDimensionMap.keySet());
        allDimIds.removeAll(cascadeDimIds);
        allDimIds.forEach(dimId -> {
            Dimension dimension = allDimensionMap.get(dimId);
            CategoryTreeNode<CategoryNodeItem> dimensionBaseVO = new CategoryTreeNode<>();
            CategoryNodeItem data = new CategoryNodeItem();
            BeanUtils.copyProperties(dimension, data);
            data.setType("dimension");
            dimensionBaseVO.setData(data);
            dataList.add(dimensionBaseVO);
        });
        return Response.ok(dataList);
    }

    @CheckCacheVersion
    @GetMapping("/list/measure")
    public Response<List<BaseInfo>> listAllMeasure(@RequestParam(value = "traceId", required = false) String traceId) {
        MetadataCache metdataCache = cacheManager.getMetadataCache();
        Map<Integer, Measure> allMeasureMap = metdataCache.getAllMeasureMap();
        List<BaseInfo> baseInfoList = allMeasureMap.values().stream().map(m -> {
            BaseInfo baseInfo = new BaseInfo();
            BeanUtils.copyProperties(m, baseInfo);
            return baseInfo;
        }).collect(Collectors.toList());
        return Response.ok(baseInfoList);
    }

    @CheckCacheVersion
    @GetMapping("/tree/dimension")
    public Response<List<CategoryTreeNode<CategoryNodeItem>>> treeAllDimension(@RequestParam(value = "traceId", required = false) String traceId) {
        MetadataCache metdataCache = cacheManager.getMetadataCache();
        Map<Integer, List<Level>> hierarchyIdLevelMap = metdataCache.getHierarchyIdLevelMap();
        Map<Integer, Dimension> allDimensionMap = metdataCache.getAllDimensionMap();
        Set<Integer> cascadeDimIds = new HashSet<>();
        Set<Integer> ignoreIds = new HashSet<>();
        List<CategoryTreeNode<CategoryNodeItem>> dataList = new ArrayList<>();
        if (!CollectionUtils.isEmpty(hierarchyIdLevelMap)) {
            hierarchyIdLevelMap.forEach((h, leves) -> {
                List<Level> levelList = leves.stream().sorted(Comparator.comparing(Level::getSequence)).collect(Collectors.toList());
                Level level = levelList.get(0);
                Integer dimId = level.getDimId();
                ignoreIds.add(dimId);
                Dimension dimension = allDimensionMap.get(dimId);
                if(dimension == null){
                   return;
                }
                DimensionCache dimensionCache = cacheManager.getDimensionCache(dimension.getId());
                if(dimensionCache == null){
                    log.info("null cache is {}", dimId);
                }
                CategoryTreeNode<CategoryNodeItem> dimensionBaseVO = new CategoryTreeNode<>();
                CategoryNodeItem data = new CategoryNodeItem();
                BeanUtils.copyProperties(dimension, data);
                data.setLevelSequence(level.getSequence());
                data.setHierarchyId(level.getHierarchyId());
                data.setDimValueCount(dimensionCache.getDimValueCount());
                data.setType("dimension");
                dimensionBaseVO.setData(data);
                List<CategoryTreeNode<CategoryNodeItem>> cascadeDimensions = new ArrayList<>();
                cascadeDimIds.add(dimId);
                for (int i = 1; i < levelList.size(); i++) {
                    CategoryTreeNode<CategoryNodeItem> cascadeDimensionBaseVO = new CategoryTreeNode<>();
                    Level sublevel = levelList.get(i);
                    Dimension d = allDimensionMap.get(sublevel.getDimId());
                    if (Objects.nonNull(d)) {
                        CategoryNodeItem item = new CategoryNodeItem();
                        BeanUtils.copyProperties(d, item);
                        item.setLevelSequence(sublevel.getSequence());
                        item.setHierarchyId(sublevel.getHierarchyId());
                        item.setType("dimension");
                        DimensionCache dc = cacheManager.getDimensionCache(d.getId());
                        item.setDimValueCount(dc.getDimValueCount());
                        cascadeDimensionBaseVO.setData(item);
                        cascadeDimensions.add(cascadeDimensionBaseVO);
                        cascadeDimIds.add(d.getId());
                    }
                }
                dimensionBaseVO.setChildren(cascadeDimensions);
                dataList.add(dimensionBaseVO);
            });
        }

        Set<Integer> allDimIds = new HashSet<>();
        allDimIds.addAll(allDimensionMap.keySet());
        allDimIds.removeAll(cascadeDimIds);
        allDimIds.forEach(dimId -> {
            Dimension dimension = allDimensionMap.get(dimId);
            CategoryTreeNode<CategoryNodeItem> dimensionBaseVO = new CategoryTreeNode<>();
            CategoryNodeItem data = new CategoryNodeItem();
            BeanUtils.copyProperties(dimension, data);
            data.setType("dimension");
            dimensionBaseVO.setData(data);
            dataList.add(dimensionBaseVO);
        });

        ArrayListMultimap<Integer, CategoryTreeNode<CategoryNodeItem>> multimap = ArrayListMultimap.create();
        List<CategoryTreeNode<CategoryNodeItem>> dimensionWithoutCategoryList = new ArrayList<>();
        dataList.forEach(d -> {
            if (d.getData().getLeafCategoryId() == null) {
                dimensionWithoutCategoryList.add(d);
            } else {
                CategoryNodeItem data = new CategoryNodeItem();
                BeanUtils.copyProperties(d, data);
                multimap.put(d.getData().getLeafCategoryId(), d);
            }
        });
        List<CategoryTreeNode<CategoryNodeItem>> categoryTreeList = categoryManager.getCategoryTreeNodes(CategoryQueryVO.builder().dim(true).build(), metdataCache);
        setData(multimap, categoryTreeList, ignoreIds);
        if (!CollectionUtils.isEmpty(dimensionWithoutCategoryList)) {
            CategoryTreeNode<CategoryNodeItem> dimensionWithoutCategoryTree = new CategoryTreeNode<>();
            CategoryNodeItem data = new CategoryNodeItem();
            data.setCnName("未分类");
            data.setType("category");
            data.setId(-100);
            dimensionWithoutCategoryTree.setData(data);
            dimensionWithoutCategoryTree.setChildren(dimensionWithoutCategoryList);
            categoryTreeList.add(dimensionWithoutCategoryTree);
        }
        return Response.ok(categoryTreeList);
    }

    private void setData(ArrayListMultimap<Integer, CategoryTreeNode<CategoryNodeItem>> multimap, List<CategoryTreeNode<CategoryNodeItem>> categoryTreeList, Set<Integer> ignoreIds) {
        if (!CollectionUtils.isEmpty(categoryTreeList)) {
            for (CategoryTreeNode<CategoryNodeItem> c : categoryTreeList) {
                if (Objects.equals(c.getData().getType(), "dimension") && ignoreIds.contains(c.getData().getId())) {
                    break;
                }
                if (Objects.equals(c.getData().getType(), "category")) {
                    List<CategoryTreeNode<CategoryNodeItem>> categoryTreeNodes = multimap.get(c.getData().getId());
                    // if (!CollectionUtils.isEmpty(categoryTreeNodes)){
                    //     categoryTreeNodes.forEach(treeNode -> {
                    //         /**
                    //          * 如果没有单独指定权限，则需要继承父节点权限
                    //          */
                    //         if (treeNode.getData().isInheritParentAuth()){
                    //             treeNode.getData().setBelongSpace(c.getData().isBelongSpace());
                    //             treeNode.getData().setHasAuth(c.getData().isHasAuth());
                    //         }
                    //     });
                    // }

                    c.getChildren().addAll(categoryTreeNodes);
                    setData(multimap, c.getChildren(), ignoreIds);
                }

            }
        }
    }

    @Autowired
    MeasureMapper measureMapper;
    @Autowired
    DimensionMapper dimensionMapper;
    @Autowired
    IDismantlingTreeService iDismantlingTreeService;

    @Autowired
    IMeasureService iMeasureService;

    @Autowired
    SourceMapper sourceMapper;

    @CheckCacheVersion
    @PostMapping("/list/byCodes")
    public Response listByCodes(@RequestBody QueryBaseInfoVO queryBaseInfoVO) {
        Map<String, Object> result = new HashMap<>();
        MetadataCache metadataCache = cacheManager.getMetadataCache();
        Map<Integer, Measure> allMeasureMap = metadataCache.getAllMeasureMap();
        Map<Integer, Dimension> allDimensionMap = metadataCache.getAllDimensionMap();
        if (!CollectionUtils.isEmpty(queryBaseInfoVO.getCodes())) {
            Map<String, Measure> measureMap = allMeasureMap.values().stream().filter(m -> queryBaseInfoVO.getCodes().contains(m.getCode())).collect(Collectors.toMap(Measure::getCode, m -> m));
            Map<Integer, Dimension> dimensionMap = allDimensionMap.values().stream().filter(d -> queryBaseInfoVO.getCodes().contains(d.getCode())).collect(Collectors.toMap(Dimension::getId, d -> d));
            Map<String, DimensionBaseVO> dimensionBaseVOMap = convert(dimensionMap, metadataCache);
            result.putAll(measureMap);
            result.putAll(dimensionBaseVOMap);
        } else {
            Map<String, Measure> measureMap = allMeasureMap.values().stream().collect(Collectors.toMap(Measure::getCode, m -> m));
            Map<String, DimensionBaseVO> dimensionBaseVOMap = convert(allDimensionMap, metadataCache);
            result.putAll(measureMap);
            result.putAll(dimensionBaseVOMap);
        }
        return Response.ok(result);
    }

    private Map<String, DimensionBaseVO> convert(Map<Integer, Dimension> targetDimensionMap, MetadataCache metadataCache) {
        Map<Integer, Dimension> allDimensionMap = metadataCache.getAllDimensionMap();
        Map<Integer, Level> dimIdLevelMap = metadataCache.getDimIdLevelMap();
        Map<Integer, List<Level>> hierarchyIdLevelMap = metadataCache.getHierarchyIdLevelMap();
        Map<String, DimensionBaseVO> dimensionMap = targetDimensionMap.values().stream().map(d -> {
            DimensionBaseVO dimensionBaseVO = new DimensionBaseVO();
            String offlineReason = "当前维度暂不可查，原因" + d.getOfflineOperator() + "在" + d.getOfflineTime().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")) + "将指标下线，下线原因：" + d.getOfflineRemark();
            dimensionBaseVO.setOfflineReason(offlineReason);
            BeanUtils.copyProperties(d, dimensionBaseVO);
            Level level = dimIdLevelMap.get(d.getId());
            if (Objects.nonNull(level)) {
                dimensionBaseVO.setHierarchyId(level.getHierarchyId());
                dimensionBaseVO.setSequence(level.getSequence());
                List<Level> levels = hierarchyIdLevelMap.get(level.getHierarchyId());
                List<DimensionBaseVO> cascadeList = levels.stream().sorted(Comparator.comparing(Level::getSequence)).map(l -> {
                    DimensionBaseVO cascade = new DimensionBaseVO();
                    Integer dimId = l.getDimId();
                    Dimension dimension = allDimensionMap.get(dimId);
                    Level subLevel = dimIdLevelMap.get(dimId);
                    BeanUtils.copyProperties(dimension, cascade);
                    cascade.setHierarchyId(subLevel.getHierarchyId());
                    cascade.setSequence(subLevel.getSequence());
                    String offlineReason2 = "当前维度暂不可查，原因" + dimension.getOfflineOperator() + "在" + dimension.getOfflineTime().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")) + "将指标下线，下线原因：" + dimension.getOfflineRemark();
                    cascade.setOfflineReason(offlineReason2);
                    return cascade;
                }).collect(Collectors.toList());
                dimensionBaseVO.setCascadeDimensions(cascadeList);
            }
            return dimensionBaseVO;
        }).collect(Collectors.toMap(DimensionBaseVO::getCode, d -> d));
        return dimensionMap;
    }

    @PostMapping("/list/dismantlingTreeName")
    @ApiOperation(value = "拆解树选择")
    public Response<List<String>> listDismantlingTreeName(@RequestBody DismantlingData query) {
//        String measureName = query.getMeasureName();
//        Measure measure = iMeasureService.getOne(Wrappers.<Measure>lambdaQuery().eq(Measure::getCnName, measureName));
        List<DismantlingTree> trees = iDismantlingTreeService.list(Wrappers.<DismantlingTree>lambdaQuery()
                .eq(DismantlingTree::getRootMeasCode, query.getCode())
                .eq(DismantlingTree::getSpaceId, query.getSpaceId()));
        List<String> names = new ArrayList<>();
        for (DismantlingTree tree : trees) {
            names.add(tree.getName());
        }
        return Response.ok(names);
    }

    @ApiOperation(value = "数据解读来源选择")
    @GetMapping("/getSourceName")
    public Response<List<String>> getSourceName() {
        List<String> list = sourceMapper.getSourceName();
        return Response.ok(list);
    }
}
