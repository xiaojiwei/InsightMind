package com.graphinsight.indicator.manager;

import com.graphinsight.indicator.util.SqlInjectionUtils;
import com.alibaba.fastjson.JSON;
import com.baomidou.dynamic.datasource.annotation.DS;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.graphinsight.indicator.auto.entity.AuthElement;
import com.graphinsight.indicator.auto.entity.ComplexMeasureDependencyTree;
import com.graphinsight.indicator.auto.entity.Dashboard;
import com.graphinsight.indicator.auto.entity.DataSource;
import com.graphinsight.indicator.auto.entity.Dimension;
import com.graphinsight.indicator.auto.entity.DimensionApplication;
import com.graphinsight.indicator.auto.entity.DimensionFilter;
import com.graphinsight.indicator.auto.entity.DimensionOperator;
import com.graphinsight.indicator.auto.entity.DimensionOperatorValue;
import com.graphinsight.indicator.auto.entity.DwColumn;
import com.graphinsight.indicator.auto.entity.DwTable;
import com.graphinsight.indicator.auto.entity.Level;
import com.graphinsight.indicator.auto.entity.Measure;
import com.graphinsight.indicator.auto.entity.MeasureApplication;
import com.graphinsight.indicator.auto.entity.MeasureNaturalDateMapping;
import com.graphinsight.indicator.auto.entity.User;
import com.graphinsight.indicator.auto.entity.Widget;
import com.graphinsight.indicator.auto.mapper.*;
import com.graphinsight.indicator.auto.service.IAuthElementService;
import com.graphinsight.indicator.auto.service.IComplexMeasureDependencyTreeService;
import com.graphinsight.indicator.auto.service.IDashboardService;
import com.graphinsight.indicator.auto.service.IDimensionFilterService;
import com.graphinsight.indicator.auto.service.IDimensionOperatorService;
import com.graphinsight.indicator.auto.service.IDimensionOperatorValueService;
import com.graphinsight.indicator.auto.service.IDimensionService;
import com.graphinsight.indicator.auto.service.IMeasureApplicationService;
import com.graphinsight.indicator.auto.service.IMeasureNaturalDateMappingService;
import com.graphinsight.indicator.auto.service.IMeasureService;
import com.graphinsight.indicator.constant.IndicatorConstant;
import com.graphinsight.indicator.enums.AvailableType;
import com.graphinsight.indicator.enums.FieldType;
import com.graphinsight.indicator.enums.ItemType;
import com.graphinsight.indicator.enums.MeasureType;
import com.graphinsight.indicator.enums.SqlAggFunType;
import com.graphinsight.indicator.enums.TableColumnType;
import com.graphinsight.indicator.enums.YesNoType;
import com.graphinsight.indicator.exception.IndicatorParamNotValidException;
import com.graphinsight.indicator.model.OperationItem;
import com.graphinsight.indicator.model.OperationItemBuilder;
import com.graphinsight.indicator.model.ReferenceCheck;
import com.graphinsight.indicator.model.Response;
import com.graphinsight.indicator.model.cache.DwTableCache;
import com.graphinsight.indicator.model.cache.MeasureApplicationCache;
import com.graphinsight.indicator.model.cache.MeasureCache;
import com.graphinsight.indicator.model.cache.MetadataCache;
import com.graphinsight.indicator.model.dto.BuildSqlParam;
import com.graphinsight.indicator.model.dto.ColumnItemExp;
import com.graphinsight.indicator.model.dto.IndicatorBean;
import com.graphinsight.indicator.model.dto.MeasureExpCreate;
import com.graphinsight.indicator.model.dto.RatioMeasureDismanling;
import com.graphinsight.indicator.model.dto.RelatedResourceDTO;
import com.graphinsight.indicator.model.vo.BaseInfo;
import com.graphinsight.indicator.model.vo.ComplexMeasureBaseVO;
import com.graphinsight.indicator.model.vo.ComplexMeasureCreateVO;
import com.graphinsight.indicator.model.vo.ComplexMeasureUpdateVO;
import com.graphinsight.indicator.model.vo.DimensionFilterCreateVO;
import com.graphinsight.indicator.model.vo.DimensionFilterOperatorCreateVO;
import com.graphinsight.indicator.model.vo.ExpressionItem;
import com.graphinsight.indicator.model.vo.MeasureBasicInfoVO;
import com.graphinsight.indicator.model.vo.MeasureCreateVO;
import com.graphinsight.indicator.model.vo.MeasureExpBaseVO;
import com.graphinsight.indicator.model.vo.MeasureExpUpdateVO;
import com.graphinsight.indicator.model.vo.MeasureOnlineCheck;
import com.graphinsight.indicator.model.vo.MeasureUpdateVO;
import com.graphinsight.indicator.model.vo.ModelVO;
import com.graphinsight.indicator.model.vo.NaturalDimConfigVO;
import com.graphinsight.indicator.model.vo.OfflineRequest;
import com.graphinsight.indicator.model.vo.OriginMeasureCreateResponseVO;
import com.graphinsight.indicator.model.vo.OriginMeasureCreateVO;
import com.graphinsight.indicator.model.vo.SummedUpDimensionQueryVO;
import com.graphinsight.indicator.service.DataSourceService;
import com.graphinsight.indicator.service.impl.MeasureMonitorReferenceServiceImpl;
import com.graphinsight.indicator.util.BuildSqlUtil;
import com.graphinsight.indicator.util.IndicatorAssert;
import com.graphinsight.indicator.util.UserThreadLocalUtil;
import com.graphinsight.indicator.util.sql.CheckWhereUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;

import javax.annotation.Resource;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

/**
 * @Author: lixiaolong
 * @Description: 指标管理服务
 * @Date: 2021/11/16
 */
@Slf4j
@Service
@DS("mysql")
public class MeasureManager {

    @Autowired
    WordValuesMapper wordValuesMapper;

    @Autowired
    IMeasureService measureService;
    @Autowired
    MeasureMapper measureMapper;
    @Autowired
    private DimensionManager dimensionManager;
    @Autowired
    private MeasureApplicationMapper measureApplicationMapper;
    @Autowired
    private DimensionApplicationMapper dimensionApplicationMapper;
    @Autowired
    private DwTableMapper dwTableMapper;
    @Autowired
    DimensionMapper dimensionMapper;
    @Autowired
    IDimensionService dimensionService;
    @Autowired
    DimensionFilterMapper dimensionFilterMapper;
    @Autowired
    DimensionOperatorMapper dimensionOperatorMapper;
    @Autowired
    IDimensionOperatorValueService dimensionOperatorValueService;
    @Autowired
    IComplexMeasureDependencyTreeService complexMeasureDependencyTreeService;
    @Autowired
    private BaseConfigureMapper baseConfigureMapper;
    @Autowired
    DataSourceService dataSourceService;
    @Autowired
    DorisQueryManager dorisQueryManager;
    @Autowired
    IMeasureNaturalDateMappingService naturalDateMappingService;
    @Resource
    IDashboardService dashboardService;
    @Resource
    ReferenceManager referenceManager;


    public MeasureOnlineCheck online(Integer id) {
        Measure measure = measureService.getById(id);
        IndicatorAssert.indicatorAssert(measure == null, "指标不存在,ID:" + id);
        MeasureOnlineCheck onlineCheck = checkOnlinable(measure);
        measure.setOnline(YesNoType.YES.getCode());
        measureService.updateById(measure);
        return onlineCheck;
    }


    /**
     * 表达式启用
     *
     * @param appId
     * @return
     */
    public void enableExp(Integer appId) {
        MeasureApplication measureApplication = measureApplicationService.getById(appId);
        IndicatorAssert.indicatorAssert(measureApplication == null, "表达式不存在");
        measureApplication.setAvailable(YesNoType.YES.getCode());
        measureApplicationService.updateById(measureApplication);
    }

    /**
     * 表达式停用
     *
     * @param appId
     * @return
     */
    @Transactional(rollbackFor = Exception.class)
    public List<RelatedResourceDTO> disabledExp(Integer appId) {
        MeasureApplication measureApplication = measureApplicationService.getById(appId);
        IndicatorAssert.indicatorAssert(measureApplication == null, "表达式不存在");
        List<RelatedResourceDTO> relatedResourceDTOS = checkRelation(appId);
        if (!CollectionUtils.isEmpty(relatedResourceDTOS)) {
            return relatedResourceDTOS;
        }
        measureApplication.setAvailable(YesNoType.NO.getCode());
        measureApplicationService.updateById(measureApplication);
        // 如果表达式时最后一个，则下线指标
        List<MeasureApplication> measureApplications = measureApplicationService.list(Wrappers.<MeasureApplication>lambdaQuery().eq(MeasureApplication::getMeasId, measureApplication.getMeasId()));
        if (measureApplications.size() == 1) {
            OfflineRequest request = new OfflineRequest();
            request.setId(measureApplication.getMeasId());
            request.setReason("最后一个表达式停用，指标自动下线");
            List<ReferenceCheck> checks = offline(request);
            if (!CollectionUtils.isEmpty(checks)) {
                // TODO 下线失败提示
                throw IndicatorParamNotValidException.error("停用表达式会使一批依赖指标下线导致相关资源报错，不允许操作");
            }
        }
        return Collections.EMPTY_LIST;
    }


    public List<RelatedResourceDTO> disabledExpCheck(Integer appId) {
        MeasureApplication measureApplication = measureApplicationService.getById(appId);
        IndicatorAssert.indicatorAssert(measureApplication == null, "表达式不存在");
        List<RelatedResourceDTO> relatedResourceDTOS = checkRelation(appId);
        if (!CollectionUtils.isEmpty(relatedResourceDTOS)) {
            return relatedResourceDTOS;
        }
        return Collections.EMPTY_LIST;
    }

    /**
     * 检查指标是否可上线，可上线的条件需满足以下情况之一
     * 1.原子指标
     * 2.依赖的复合/派生指标都在线
     *
     * @param measure
     */
    public MeasureOnlineCheck checkOnlinable(Measure measure) {
        MeasureOnlineCheck check = new MeasureOnlineCheck();
        Map<Integer, ComplexMeasureBaseVO> result = new HashMap<>();
        List<MeasureApplication> applications = measureApplicationService.list(Wrappers.<MeasureApplication>lambdaQuery().eq(MeasureApplication::getMeasId, measure.getId()));
        for (MeasureApplication application : applications) {
            List<BaseInfo> baseInfos = new ArrayList<>();
            // if (Objects.equals(application.getApplyType(), MeasureType.ORIGIN.getCode())) {
            //     // 原子指标
            //     if (Objects.nonNull(application.getDwTableId())) {
            //         DwTable dwTable = dwTableMapper.selectById(application.getDwTableId());
            //         if (Objects.equals(dwTable.getOnline(), YesNoType.NO.getCode())) {
            //             BaseInfo baseInfo = new BaseInfo();
            //             BeanUtils.copyProperties(dwTable, baseInfo);
            //             baseInfos.add(baseInfo);
            //         }
            //     }
            // }
            List<ComplexMeasureDependencyTree> trees = complexMeasureDependencyTreeService.list(Wrappers.<ComplexMeasureDependencyTree>lambdaQuery().eq(ComplexMeasureDependencyTree::getMeasAppId, application.getId()));
            List<Integer> measIds = trees.stream().filter(tree -> Objects.equals(tree.getDependencyType(), TableColumnType.MEASURE.getCode())).map(ComplexMeasureDependencyTree::getDependencyId).collect(Collectors.toList());
            List<Integer> dimIds = trees.stream().filter(tree -> Objects.equals(tree.getDependencyType(), TableColumnType.DIMENSION.getCode())).map(ComplexMeasureDependencyTree::getDependencyId).collect(Collectors.toList());
            if (!CollectionUtils.isEmpty(measIds)) {
                List<Measure> offlineMeasures = measureService.listByIds(measIds).stream().filter(m -> Objects.equals(m.getOnline(), YesNoType.NO.getCode())).collect(Collectors.toList());
                if (!CollectionUtils.isEmpty(offlineMeasures)) {
                    List<BaseInfo> infoList = offlineMeasures.stream().map(m -> {
                        BaseInfo baseInfo = new BaseInfo();
                        BeanUtils.copyProperties(m, baseInfo);
                        return baseInfo;
                    }).collect(Collectors.toList());
                    baseInfos.addAll(infoList);
                }
            }

            if (!CollectionUtils.isEmpty(dimIds)) {
                List<Dimension> offlineDimensions = dimensionService.listByIds(dimIds).stream().filter(m -> Objects.equals(m.getOnline(), YesNoType.NO.getCode())).collect(Collectors.toList());
                if (!CollectionUtils.isEmpty(offlineDimensions)) {
                    List<BaseInfo> infoList = offlineDimensions.stream().map(d -> {
                        BaseInfo baseInfo = new BaseInfo();
                        BeanUtils.copyProperties(d, baseInfo);
                        return baseInfo;
                    }).collect(Collectors.toList());
                    baseInfos.addAll(infoList);
                }
            }
            if (!CollectionUtils.isEmpty(baseInfos)) {
                ComplexMeasureBaseVO expresstion = getExpresstion(application.getId());
                result.put(application.getId(), expresstion);
            }
        }
        check.setListMap(result);
        check.setOnlineable(result.size() < applications.size());
        return check;
    }

    public List<ReferenceCheck> offlineCheck(OfflineRequest request) {
        Integer id = request.getId();
        Measure measure = measureService.getById(id);
        IndicatorAssert.indicatorAssert(measure == null, "指标不存在,ID:" + id);
        List<Measure> checkList = new ArrayList<>();
        checkList.add(measure);
        // 查询下游指标
        List<Measure> measures = listDownStreamMeasureWithOnlyOneExp(id);
        if (!CollectionUtils.isEmpty(measures)) {
            checkList.addAll(measures);
        }

        List<ReferenceCheck> checks = new ArrayList<>();
        for (Measure m : checkList) {
            IndicatorBean bean = new IndicatorBean();
            bean.setType(FieldType.MEASURE);
            bean.setCode(m.getCode());
            List<RelatedResourceDTO> relatedResourceDTOS = referenceManager.listRelatedResource(bean);
            if (!CollectionUtils.isEmpty(relatedResourceDTOS)) {
                ReferenceCheck check = new ReferenceCheck();
                check.setMeasure(m);
                check.setRelatedResourceDTOList(relatedResourceDTOS);
                checks.add(check);
            }
        }
        return checks;
    }

    public List<ReferenceCheck> offline(OfflineRequest request) {
        List<ReferenceCheck> checks = offlineCheck(request);
        Integer id = request.getId();
        Measure measure = measureService.getById(id);
        IndicatorAssert.indicatorAssert(measure == null, "指标不存在,ID:" + id);
        if (CollectionUtils.isEmpty(checks)) {
            // 检查通过再更新
            measure.setOnline(YesNoType.NO.getCode());
            measure.setOfflineRemark(request.getReason());
            measure.setOfflineOperator(UserThreadLocalUtil.getUserName());
            measure.setOfflineTime(LocalDateTime.now());
            measureService.updateById(measure);
        }
        return checks;
    }


    /**
     * 获取依赖目标指标且仅只有这一个表达式的所有下游指标
     *
     * @param id
     * @return
     */
    private List<Measure> listDownStreamMeasureWithOnlyOneExp(Integer id) {
        List<Measure> downStreamMeasures = new ArrayList<>();
        List<ComplexMeasureDependencyTree> trees = complexMeasureDependencyTreeService.list(Wrappers.<ComplexMeasureDependencyTree>lambdaQuery().eq(ComplexMeasureDependencyTree::getDependencyId, id));
        if (!CollectionUtils.isEmpty(trees)) {
            List<Integer> measIds = trees.stream().map(ComplexMeasureDependencyTree::getComplexMeasId).collect(Collectors.toList());
            Map<Integer, List<ComplexMeasureDependencyTree>> measAppDependencyTreeMap = complexMeasureDependencyTreeService.list().stream().collect(Collectors.groupingBy(ComplexMeasureDependencyTree::getMeasAppId));
            List<Measure> measures = measureService.listByIds(measIds);
            Map<Integer, Measure> measureMap = measures.stream().collect(Collectors.toMap(Measure::getId, m -> m));
            List<MeasureApplication> measureApplications = measureApplicationService.list(Wrappers.<MeasureApplication>lambdaQuery().in(MeasureApplication::getMeasId, measureMap.keySet()));
            Map<Integer, List<MeasureApplication>> measureAppMap = measureApplications.stream().collect(Collectors.groupingBy(MeasureApplication::getMeasId));
            for (Map.Entry<Integer, List<MeasureApplication>> entry : measureAppMap.entrySet()) {
                boolean addFlag = true;
                List<MeasureApplication> exps = entry.getValue();
                if (exps.size() > 1) {
                    // 有多个表达式，需要看是否都依赖目标指标
                    List<MeasureApplication> applications = entry.getValue();
                    for (MeasureApplication measureApplication : applications) {
                        List<ComplexMeasureDependencyTree> complexMeasureDependencyTrees = measAppDependencyTreeMap.get(measureApplication.getId());
                        if (CollectionUtils.isEmpty(complexMeasureDependencyTrees)) {
                            // 如果为空，说明该下游指标有原子指标类型，不满足条件，直接跳过
                            addFlag = false;
                            break;
                        }
                        ComplexMeasureDependencyTree dependencyTree = complexMeasureDependencyTrees.stream()
                                .filter(tree -> Objects.equals(tree.getDependencyType(), TableColumnType.MEASURE.getCode())) // 类型是指标
                                .filter(tree -> Objects.equals(tree.getDependencyId(), id)) // 依赖目标指标
                                .findAny()
                                .orElse(null);

                        if (dependencyTree == null) {
                            // 此表达式没有找到依赖目标指标的依赖树，说明不唯一依赖目标指标，不满足条件
                            addFlag = false;
                            break;
                        }
                    }
                }
                if (addFlag) {
                    downStreamMeasures.add(measureMap.get(entry.getKey()));
                }
            }
        }
        return downStreamMeasures;
    }


    @Resource
    MeasureMonitorReferenceServiceImpl measureMonitorReferenceService;

    /**
     * 只在删除或者修改时校验
     *
     * @param expressionId
     * @return
     */
    public List<RelatedResourceDTO> checkRelation(Integer expressionId) {
        MeasureApplication application = measureApplicationService.getById(expressionId);
        MeasureCache cache = cacheManager.getMeasureCache(application.getMeasId());
        if (cache == null) {
            return Collections.EMPTY_LIST;
        }

        List<MeasureApplicationCache> list = cache.getMeasureApplicationCacheList();
        List<MeasureApplicationCache> measureApplicationCaches = list.stream().filter(c -> c.getMeasAppId().intValue() == expressionId.intValue()).collect(Collectors.toList());
        if (CollectionUtils.isEmpty(list)) {
            return Collections.EMPTY_LIST;
        }
        if (CollectionUtils.isEmpty(measureApplicationCaches)) {
            return Collections.EMPTY_LIST;
        }


        List<RelatedResourceDTO> result = new ArrayList<>();
        Set<Integer> allDimIds = new HashSet();
        Set<Integer> tableIds = new HashSet();
        for (MeasureApplicationCache measureApplicationCache : measureApplicationCaches) {
            Integer dwTableId = measureApplicationCache.getRelatedDwTableId();
            long count = list.stream().filter(c -> Objects.equals(c.getRelatedDwTableId(), dwTableId)).count();
            if (count > 1) {
                continue;
            }
            // 一个指标去掉一个唯一的模型以后，少了哪些维
            DwTableCache tableCache = cacheManager.getDwTableCache(dwTableId);
            // 这个模型所有的相关维度
            Set<Integer> relatedDimensionIds = tableCache.getRelatedDimensionIds();
            allDimIds.addAll(relatedDimensionIds);
            tableIds.add(dwTableId);
        }

        Set<Integer> tempDimIds = new HashSet();
        tempDimIds.addAll(allDimIds);
        for (Integer tableId : cache.getRelatedDwTableIds()) {
            if (!tableIds.contains(tableId)) {
                DwTableCache checkCache = cacheManager.getDwTableCache(tableId);
                Set<Integer> dimensionIds = checkCache.getRelatedDimensionIds();
                tempDimIds.removeAll(dimensionIds);
            }
        }

        if (CollectionUtils.isEmpty(tempDimIds)) {
            return result;
        }
        MeasureCache measureCache = cacheManager.getMeasureCache(application.getMeasId());
        Map<Long, Widget> widgetMap = measureCache.getRelatedWidgets().stream().collect(Collectors.toMap(Widget::getId, w -> w));
        Map<Long, DataSource> dataSourceMap = measureCache.getRelatedDataSources().stream().collect(Collectors.toMap(DataSource::getId, d -> d));
        IndicatorBean measureBean = new IndicatorBean();
        measureBean.setCode(measureCache.getCode());
        List<RelatedResourceDTO> measureDtos = measureMonitorReferenceService.listRelatedResource(measureBean);

        Set<Long> widgetIds = new HashSet<>();
        Set<Long> dsIds = new HashSet<>();

        for (Integer tempDimId : tempDimIds) {
            Set<Long> widgets = cacheManager.getDimensionCache(tempDimId).getRelatedWidgets().stream().filter(w -> widgetMap.keySet().contains(w.getId())).map(Widget::getId).collect(Collectors.toSet());
            widgetIds.addAll(widgets);

            Set<Long> datasources = cacheManager.getDimensionCache(tempDimId).getRelatedDataSources().stream().filter(w -> dataSourceMap.keySet().contains(w.getId())).map(DataSource::getId).collect(Collectors.toSet());
            dsIds.addAll(datasources);

            IndicatorBean bean = new IndicatorBean();
            bean.setCode(cacheManager.getDimensionCache(tempDimId).getCode());
            if (!CollectionUtils.isEmpty(measureDtos)) {
                Set<String> resourceIds = measureDtos.stream().map(dto -> dto.getResourceId() + "_" + dto.getType()).collect(Collectors.toSet());
                List<RelatedResourceDTO> dtos = measureMonitorReferenceService.listRelatedResource(bean);
                List<RelatedResourceDTO> resourceDTOS = dtos.stream().filter(dto -> resourceIds.contains(dto.getResourceId() + "_" + dto.getType())).collect(Collectors.toList());
                if (!CollectionUtils.isEmpty(resourceDTOS)) {
                    result.addAll(resourceDTOS);
                }
            }

        }

        if (CollectionUtils.isEmpty(widgetIds) && CollectionUtils.isEmpty(dsIds)) {
            return result;
        }

        if (!CollectionUtils.isEmpty(widgetIds)) {
            List<Dashboard> dashboards = dashboardService.list();
            Map<Long, Dashboard> dashboardMap = dashboards.stream().collect(Collectors.toMap(Dashboard::getId, d -> d));
            List<RelatedResourceDTO> dtos = widgetIds.stream().map(id -> {
                RelatedResourceDTO dto = new RelatedResourceDTO();
                Widget widget = widgetMap.get(id);
                dto.setResourceId(widget.getDashboardId());
                dto.setType(1);
                dto.setTypeName("数据看板");
                dto.setName(widget.getName());
                Long spaceId = dashboardMap.get(widget.getDashboardId()).getSpaceId();
                dto.setSpaceId(spaceId);
                return dto;
            }).collect(Collectors.toList());
            result.addAll(dtos);
        }

        if (!CollectionUtils.isEmpty(dsIds)) {
            List<RelatedResourceDTO> dtos = dsIds.stream().map(id -> {
                RelatedResourceDTO dto = new RelatedResourceDTO();
                dto.setResourceId(id);
                dto.setName(dataSourceMap.get(id).getName());
                dto.setTypeName("数据集");
                dto.setSpaceId(dataSourceMap.get(id).getSpaceId());
                dto.setType(0);
                return dto;
            }).collect(Collectors.toList());
            result.addAll(dtos);
        }

        return result;
    }

    public List<Dimension> listCanBeSummedUpDimension(SummedUpDimensionQueryVO summedUpDimensionQueryVO) {
        DwTableCache dwTableCache = cacheManager.getDwTableCache(summedUpDimensionQueryVO.getModelId().intValue());
        if (dwTableCache == null) {
            return Collections.EMPTY_LIST;
        }
        Set<Integer> relatedDimensionIds = dwTableCache.getRelatedDimensionIds();
        Set<Integer> dimIds = relatedDimensionIds.stream()
                .map(dimId -> {
                    List<Level> levels = dimensionManager.getLeves(dimId);
                    if (CollectionUtils.isEmpty(levels)) {
                        return dimId;
                    } else {
                        List<Integer> sortedIds = levels.stream().sorted(Comparator.comparing(Level::getSequence).reversed()).map(Level::getDimId).collect(Collectors.toList());
                        for (Integer sortedId : sortedIds) {
                            if (relatedDimensionIds.contains(sortedId)) {
                                return sortedId;
                            }
                        }
                        return null;
                    }
                })
                .filter(d -> d != null)
                .collect(Collectors.toSet());
        if (CollectionUtils.isEmpty(dimIds)) {
            return Collections.EMPTY_LIST;
        }

        return dimIds.stream().map(dimId -> dimensionService.getById(dimId)).filter(dimension -> Objects.nonNull(dimension) && Objects.equals(dimension.getIsHyper(), YesNoType.NO.getCode())).collect(Collectors.toList());
    }

    public void removeNaturalDimensionConfig(NaturalDimConfigVO dimConfigVO) {
        if (dimConfigVO != null) {
            naturalDateMappingService.remove(Wrappers.<MeasureNaturalDateMapping>lambdaQuery()
                    .eq(MeasureNaturalDateMapping::getMeasId, dimConfigVO.getMeasId())
                    .eq(MeasureNaturalDateMapping::getDwTableId, dimConfigVO.getModelId()));
        }
    }

    public void deleteNaturalDimensionConfig(Long measId, Long modelId) {
        naturalDateMappingService.remove(Wrappers.<MeasureNaturalDateMapping>lambdaQuery()
                .eq(MeasureNaturalDateMapping::getMeasId, measId)
                .eq(MeasureNaturalDateMapping::getDwTableId, modelId));
    }

    public void naturalDimensionConfig(List<NaturalDimConfigVO> dimConfigVOS) {
        for (NaturalDimConfigVO dimConfigVO : dimConfigVOS) {
            Long dimId = dimConfigVO.getDimId();
            if (dimId != null) {
                Dimension dimension = dimensionService.getById(dimId);
                if (dimension == null) {
                    throw IndicatorParamNotValidException.error("维度不存在");
                }
                MeasureNaturalDateMapping measureNaturalDateMapping = new MeasureNaturalDateMapping();
                measureNaturalDateMapping.setMeasId(dimConfigVO.getMeasId());
                measureNaturalDateMapping.setDwTableId(dimConfigVO.getModelId());
                measureNaturalDateMapping.setTargetDimId(dimConfigVO.getDimId());
                measureNaturalDateMapping.setNaturalDimId(dimConfigVO.getHyperDimId());
                naturalDateMappingService.saveOrUpdate(measureNaturalDateMapping, Wrappers.<MeasureNaturalDateMapping>lambdaQuery()
                        .eq(MeasureNaturalDateMapping::getMeasId, dimConfigVO.getMeasId().intValue())
                        .eq(MeasureNaturalDateMapping::getDwTableId, dimConfigVO.getModelId().intValue())
                        .eq(MeasureNaturalDateMapping::getTargetDimId, dimConfigVO.getDimId().intValue())
                );
            }
        }
    }


    public boolean canDrillDown(String measCode, Set<String> dimCodeSet) {

        return true;

        /**
         * TODO 是否能下钻的开关先放开，默认所有指标都能下钻 如果后续有限制，打开以下注释，重新调整
         */
        // MetadataCache metadataCache = cacheManager.getMetadataCache();
        // Map<String, Measure> allMeasureCodeMap = metadataCache.getAllMeasureCodeMap();
        // Measure measure = allMeasureCodeMap.get(measCode);
        // MeasureCache measureCache = cacheManager.getMeasureCache(measure.getId());
        // List<Integer> detailDwTableIds = measureCache.getDetailDwTableIds();
        // if (CollectionUtils.isEmpty(detailDwTableIds)) {
        //     return false;
        // }
        // if (CollectionUtils.isEmpty(dimCodeSet)) {
        //     return true;
        // }
        // Map<String, Dimension> allDimensionCodeMap = metadataCache.getAllDimensionCodeMap();
        // List<Dimension> dimensions = dimCodeSet.stream().map(dimCode -> allDimensionCodeMap.get(dimCode)).collect(Collectors.toList());
        // Integer dwTableId = detailDwTableIds.get(0);
        // DwTableCache dwTableCache = cacheManager.getDwTableCache(dwTableId);
        // List<DwColumn> dwColumnList = dwTableCache.getDwColumnList();
        // Dimension naturalDimensionMapping = dimensionManager.getNaturalDimension(measure.getId());
        // Set<String> tableColumns = dwColumnList.stream().map(DwColumn::getName).collect(Collectors.toSet());
        // for (Dimension dimension : dimensions) {
        //     List<Dimension> seqDimensions = dimensionManager.listLeSeqDimensions(dimension.getId());
        //     Set<String> enNames = seqDimensions.stream().map(Dimension::getEnName).collect(Collectors.toSet());
        //     if (naturalDimensionMapping != null){
        //         enNames.add(naturalDimensionMapping.getEnName());
        //     }
        //     boolean containsAny = false;
        //     for (String enName : enNames) {
        //         if (tableColumns.contains(enName)) {
        //             containsAny = true;
        //             break;
        //         }
        //     }
        //     if (!containsAny) {
        //         return false;
        //     }
        // }
        // return true;
    }

    /**
     * TODO 如果有多个明细模型，就需要调用这个方法看具体哪个模型能下钻
     *
     * @param tableId
     * @param dimensions
     * @return
     */
    private boolean canDrillDown(Integer tableId, List<Dimension> dimensions) {
        DwTableCache dwTableCache = cacheManager.getDwTableCache(tableId);
        List<DwColumn> dwColumnList = dwTableCache.getDwColumnList();
        Set<String> tableColumns = dwColumnList.stream().map(DwColumn::getName).collect(Collectors.toSet());
        for (Dimension dimension : dimensions) {
            List<Dimension> seqDimensions = dimensionManager.listLeSeqDimensions(dimension.getId());
            Set<String> enNames = seqDimensions.stream().map(Dimension::getEnName).collect(Collectors.toSet());
            boolean containsAny = false;
            for (String enName : enNames) {
                if (tableColumns.contains(enName)) {
                    containsAny = true;
                    break;
                }
            }
            if (!containsAny) {
                return false;
            }
        }
        return true;
    }

    /**
     * 获取A/B 类型的指标拆解结果
     *
     * @param measCode
     * @return
     */
    public RatioMeasureDismanling getRatioMeasureDismanling(String measCode) {
        RatioMeasureDismanling result = new RatioMeasureDismanling();
        Measure one = measureService.getOne(Wrappers.<Measure>lambdaQuery().eq(Measure::getCode, measCode));
        if (Objects.isNull(one)) {
            return result;
        }
        List<MeasureApplication> measureApplicationList = measureApplicationService.list(Wrappers.<MeasureApplication>lambdaQuery()
                .eq(MeasureApplication::getMeasId, one.getId())
                .eq(MeasureApplication::getApplyType, MeasureType.DERIVED.getCode()));

        if (CollectionUtils.isEmpty(measureApplicationList)) {
            return result;
        }
        RatioMeasureDismanling measureDismanling = measureApplicationList.stream()
                .map(ma -> {
                    String expression = ma.getExpression();
                    List<ExpressionItem> expressionItemVOList = JSON.parseArray(expression, ExpressionItem.class);
                    return expressionItemVOList;
                })
                .filter(expressionItemVOS -> expressionItemVOS.size() == 3)
                .filter(expressionItemVOS -> {
                    ExpressionItem molecularItem = expressionItemVOS.get(0);
                    ExpressionItem divideItem = expressionItemVOS.get(1);
                    ExpressionItem denominatorItem = expressionItemVOS.get(2);
                    if (Objects.isNull(molecularItem.getOperand())
                            || Objects.isNull(denominatorItem.getOperand())
                            || !Objects.equals(divideItem.getOperator(), "/")) {
                        return false;
                    }
                    return true;
                })
                .findFirst()
                .map(expressionItemVOS -> {
                    RatioMeasureDismanling r = new RatioMeasureDismanling();
                    ExpressionItem molecularItem = expressionItemVOS.get(0);
                    ExpressionItem denominatorItem = expressionItemVOS.get(2);
                    r.setComputable(true);
                    r.setDenominatorCode(measureService.getById(denominatorItem.getOperand().getId()).getCode());
                    r.setMolecularCode(measureService.getById(molecularItem.getOperand().getId()).getCode());
                    return r;
                })
                .orElse(result);
        return measureDismanling;
    }

    /**
     * 判断指标是否是比率型指标
     *
     * @return
     */
    public boolean isRatio(String measCode) {
        Measure one = measureService.getOne(Wrappers.<Measure>lambdaQuery().eq(Measure::getCode, measCode));
        if (Objects.nonNull(one) && Objects.nonNull(one.getUnit())) {
            if (Objects.equals(one.getUnit(), "%")) {
                return true;
            } else {
                return false;
            }
        }
        return false;
    }


    @Transactional(rollbackFor = Exception.class)
    public Response createExpression(MeasureExpBaseVO measureExpBaseVO) {
        Measure measure = measureService.getById(measureExpBaseVO.getMeasureId());
        if (Objects.equals(MeasureType.ORIGIN.getCode(), measureExpBaseVO.getMeasureType())) {
            if (null != measureExpBaseVO.getWhereCondition() && !measureExpBaseVO.getWhereCondition().isEmpty()) {
                CheckWhereUtil.setWhereCheckFlag(0xffffffff);
                CheckWhereUtil.checkWhere("where " + measureExpBaseVO.getWhereCondition());
            }

            if (SqlInjectionUtils.check(measureExpBaseVO.getColumnEnName())) {
                throw new IllegalArgumentException("非法字段请求");
            }
            MeasureExpCreate measureExpCreate = new MeasureExpCreate();
            BeanUtils.copyProperties(measureExpBaseVO, measureExpCreate);
            // 配置自然日维度
            if (measureExpBaseVO.getNaturalDimConfig() != null) {
                naturalDimensionConfig(measureExpBaseVO.getNaturalDimConfig());
            }
            return createOriginExpresionForExitsMeasure(measureExpCreate, measure);
        } else {
            circularDependencyCheck(measureExpBaseVO);
            ComplexMeasureBaseVO complexMeasure = new ComplexMeasureBaseVO();
            BeanUtils.copyProperties(measureExpBaseVO, complexMeasure);
            complexMeasure.setId(measureExpBaseVO.getMeasureId());
            createComplexExpressionMeasure(complexMeasure);
        }
        return Response.ok();
    }

    public Response doUpdate(MeasureExpUpdateVO measureExpBaseVO) {
        Measure measure = measureService.getById(measureExpBaseVO.getMeasureId());
        if (Objects.equals(MeasureType.ORIGIN.getCode(), measureExpBaseVO.getMeasureType())) {
            return updateOriginExpresionForExitsMeasure(measureExpBaseVO, measure);
        } else {
            circularDependencyCheck(measureExpBaseVO);
            ComplexMeasureUpdateVO complexMeasure = new ComplexMeasureUpdateVO();
            BeanUtils.copyProperties(measureExpBaseVO, complexMeasure);
            complexMeasure.setId(measureExpBaseVO.getMeasureId());
            updateComplexExpressionMeasure(complexMeasure);
        }
        return Response.ok();
    }

    @Transactional(rollbackFor = Exception.class)
    public Response updateExpression(MeasureExpUpdateVO measureExpUpdateVO) {
        if (!Objects.equals(MeasureType.ORIGIN.getCode(), measureExpUpdateVO.getMeasureType())) {
            // 非原子指标检查是否有循环依赖
            circularDependencyCheck(measureExpUpdateVO);
        }
        Integer measAppId = measureExpUpdateVO.getMeasAppId();
        List<DimensionFilter> oldFilters = dimensionFilterService.list(Wrappers.<DimensionFilter>lambdaQuery().eq(DimensionFilter::getMeasAppId, measAppId));
        removeFilters(oldFilters);
        // 删除指标依赖树
        complexMeasureDependencyTreeService.remove(Wrappers.<ComplexMeasureDependencyTree>lambdaQuery().eq(ComplexMeasureDependencyTree::getMeasAppId, measAppId));
        if (measureExpUpdateVO.getModelId() != null) {
            deleteNaturalDimensionConfig(measureExpUpdateVO.getMeasureId().longValue(), measureExpUpdateVO.getModelId().longValue());
        }
        // 添加自然维度配置
        if (measureExpUpdateVO.getNaturalDimConfig() != null) {
            naturalDimensionConfig(measureExpUpdateVO.getNaturalDimConfig());
        }
        Response expression = doUpdate(measureExpUpdateVO);
        // Set<Integer> unvaliableMeasIds = unavailableComplexMeasures(measureExpUpdateVO.getMeasureId());
        // if (! CollectionUtils.isEmpty(unvaliableMeasIds)){
        //     List<Measure> measureList = measureMapper.selectBatchIds(unvaliableMeasIds);
        //     throw IndicatorParamNotValidException.error("更新失败! 因为该操作会使指标:" + JSON.toJSONString(measureList.stream().map(Measure::getCnName)) + "不可用");
        // }
        return expression;
    }

    @Transactional(rollbackFor = Exception.class)
    public List<RelatedResourceDTO> deleteExpression(Integer measAppId) {
        List<RelatedResourceDTO> relatedResourceDTOS = checkRelation(measAppId);
        if (!CollectionUtils.isEmpty(relatedResourceDTOS)) {
            return relatedResourceDTOS;
        }
        MeasureApplication measureApplication = measureApplicationService.getById(measAppId);
        List<DimensionFilter> oldFilters = dimensionFilterService.list(Wrappers.<DimensionFilter>lambdaQuery().eq(DimensionFilter::getMeasAppId, measAppId));
        removeFilters(oldFilters);
        // 删除指标应用表
        measureApplicationMapper.deleteById(measAppId);
        // 删除指标依赖树
        complexMeasureDependencyTreeService.remove(Wrappers.<ComplexMeasureDependencyTree>lambdaQuery().eq(ComplexMeasureDependencyTree::getMeasAppId, measAppId));
        // Set<Integer> unvaliableMeasIds = unavailableComplexMeasures(measureApplication.getMeasId());
        // if (! CollectionUtils.isEmpty(unvaliableMeasIds)){
        //     List<Measure> measureList = measureMapper.selectBatchIds(unvaliableMeasIds);
        //     throw IndicatorParamNotValidException.error("删除失败! 因为删除此指标会使指标:" + JSON.toJSONString(measureList.stream().map(Measure::getCnName)) + "不可用");
        // }
        naturalDateMappingService.remove(Wrappers.<MeasureNaturalDateMapping>lambdaQuery()
                .eq(MeasureNaturalDateMapping::getMeasId, measureApplication.getMeasId())
                .eq(MeasureNaturalDateMapping::getDwTableId, measureApplication.getDwTableId()));
        cacheManager.reloadCache();

        return Collections.EMPTY_LIST;

    }

    // /** TODO 逻辑待完善
    //  * 获取用当前指标生成的计算指标中，哪些不可用了
    //  * @param measId 被依赖的指标ID
    //  * @return
    //  */
    // public Set<Integer> unavailableComplexMeasures(Integer measId){
    //     // 找到当前修改的指标的相关维度
    //     Set<Integer> targetMeasureRelatedDimensionIds = fetchRelatedDimensionFromDB(measId);
    //     Set<Integer> result = new HashSet<>();
    //     // 找到依赖这个指标的所有计算指标
    //     List<ComplexMeasureDependencyTree> complexMeasureDependencyTrees = complexMeasureDependencyTreeService.list(Wrappers.<ComplexMeasureDependencyTree>lambdaQuery()
    //             .eq(ComplexMeasureDependencyTree::getDependencyId, measId)
    //             .eq(ComplexMeasureDependencyTree::getDependencyType, TableColumnType.MEASURE.getCode()));
    //     if (CollectionUtils.isEmpty(complexMeasureDependencyTrees)){
    //         return result;
    //     }
    //     // 找到每一个计算指标的相关维度,判断是否和目标指标是否有维度交叉
    //     complexMeasureDependencyTrees.forEach(cmdt -> {
    //         Integer complexMeasId = cmdt.getComplexMeasId();
    //         Set<Integer> relatedDimensionIds = fetchRelatedDimensionFromDB(complexMeasId);
    //         if(! IndicatorCollectionUtil.hasCross(targetMeasureRelatedDimensionIds,relatedDimensionIds)){
    //             // 没有维度交叉
    //             result.add(complexMeasId);
    //         }
    //     });
    //     return result;
    // }


    public String buildeOriginMeasureQuerySql(DwTable dwTable, List<MeasureApplication> measureApplications) {
        if (CollectionUtils.isEmpty(measureApplications)) {
            throw IndicatorParamNotValidException.error("指标信息为空");
        }

        BuildSqlParam buildSqlParam = new BuildSqlParam();
        List<MeasureApplication> complexMeasApps = measureApplications.stream().filter(ma -> !Objects.equals(ma.getApplyType(), MeasureType.ORIGIN.getCode())).collect(Collectors.toList());
        if (!CollectionUtils.isEmpty(complexMeasApps)) {
            throw IndicatorParamNotValidException.error("不支持计算指标的sql生成");
        }

        List<ColumnItemExp> columnItemExpList = measureApplications.stream().map(ma -> {
            String expression = ma.getExpression();
            List<OperationItem> operationItems = JSON.parseArray(expression, OperationItem.class);
            OperationItem operationItem = operationItems.stream().findFirst().orElseThrow(() -> IndicatorParamNotValidException.error("指标表达式不存在"));
            String operator = operationItem.getOperator();
            SqlAggFunType sqlAggFunType = SqlAggFunType.valueOfDesc(operator);
            if (Objects.isNull(sqlAggFunType)) {
                throw IndicatorParamNotValidException.error("指标配置的聚合函数不合法,列名:" + ma.getFactColumn());
            }
            ColumnItemExp columnItemExp = new ColumnItemExp(ma.getFactColumn(), sqlAggFunType);
            columnItemExp.setWhereCondition(ma.getWhereCondition());
            return columnItemExp;
        }).collect(Collectors.toList());
        buildSqlParam.setFactTable(dwTable.getTableName());
        buildSqlParam.setLimit0(true);
        buildSqlParam.setSchema(dwTable.getSchemaName());
        buildSqlParam.setColumnExps(columnItemExpList);

        return BuildSqlUtil.buildSql(buildSqlParam);
    }


    public List<ComplexMeasureBaseVO> getExpressionListFromDB(Integer measureId) {
        List<ComplexMeasureBaseVO> result = new ArrayList<>();
        List<MeasureApplication> measureApplications = measureApplicationService.list(Wrappers.<MeasureApplication>lambdaQuery().eq(MeasureApplication::getMeasId, measureId));
        if (!CollectionUtils.isEmpty(measureApplications)) {
            measureApplications.forEach(ma -> {
                Integer measAppId = ma.getId();
                ComplexMeasureBaseVO expresstion = getExpresstion(measAppId);
                expresstion.setMeasAppId(measAppId);
                result.add(expresstion);
            });
        }
        return result;
    }

    public List<ComplexMeasureBaseVO> getExpressionList(Integer measureId) {
        List<MeasureApplicationCache> measureApplicationCaches = Optional.ofNullable(cacheManager.getMeasureCache(measureId))
                .map(cache -> cache.getMeasureApplicationCacheList())
                .orElseGet(ArrayList::new);
        Map<Integer, List<MeasureApplicationCache>> map = measureApplicationCaches.stream().collect(Collectors.groupingBy(MeasureApplicationCache::getMeasAppId));
        List<ComplexMeasureBaseVO> result = new ArrayList<>();
        map.forEach((id, list) -> {
            MeasureApplicationCache measureApplicationCache = list.get(0);
            Integer measAppId = measureApplicationCache.getMeasAppId();
            ComplexMeasureBaseVO expresstion = getExpresstion(measAppId);
            expresstion.setMeasAppId(measAppId);
            result.add(expresstion);
        });
        return result;
    }

    public List<ComplexMeasureBaseVO> getExpressionUnderRelatedModel(Integer measId, Integer modelId) {
        List<ComplexMeasureBaseVO> result = new ArrayList<>();
        MeasureCache measureCache = cacheManager.getMeasureCache(measId);
        List<MeasureApplicationCache> measureApplicationCacheList = measureCache.getMeasureApplicationCacheList();
        List<MeasureApplicationCache> measureApplicationCachesUnderModel = measureApplicationCacheList.stream().filter(ma -> ma.getRelatedDwTableId().equals(modelId)).collect(Collectors.toList());
        if (!CollectionUtils.isEmpty(measureApplicationCachesUnderModel)) {
            measureApplicationCachesUnderModel.forEach(ma -> {
                Integer measAppId = ma.getMeasAppId();
                ComplexMeasureBaseVO expresstion = getExpresstion(measAppId);
                expresstion.setMeasAppId(measAppId);
                result.add(expresstion);
            });
        }
        return result;
    }

    public ComplexMeasureBaseVO getExpresstion(Integer measAppId) {
        ComplexMeasureBaseVO result = new ComplexMeasureBaseVO();
        MeasureApplication measureApplication = measureApplicationService.getById(measAppId);
        if (Objects.isNull(measureApplication)) {
            return result;
        }
        Integer measId = measureApplication.getMeasId();
        Measure measure = measureMapper.selectById(measId);
        if (Objects.equals(measureApplication.getApplyType(), MeasureType.EXTENDED.getCode())) {
            // 派生指标
            result.setDimensionFilterList(listDimensionFilters(measAppId));
        }
        result.setCnName(measure.getCnName());
        result.setEnName(measure.getEnName());
        result.setId(measId);
        result.setLeafCategoryId(measure.getLeafCategoryId());
        result.setMeasureType(measureApplication.getApplyType());
        result.setAvailable(measureApplication.getAvailable());
        String expression = measureApplication.getExpression();
        LinkedList<ExpressionItem> expressionItemVOS = new LinkedList<>();
        List<OperationItem> operationItems = JSON.parseArray(expression, OperationItem.class);
        operationItems.forEach(oi -> {
            if (OperationItem.OPERATOR.equalsIgnoreCase(oi.getOperatingType())) {
                SqlAggFunType sqlAggFunType = SqlAggFunType.valueOfDesc(oi.getOperator());
                result.setSqlAggFunType(sqlAggFunType);
                result.setColumnEnName(measureApplication.getFactColumn());
            }
            ExpressionItem vo = new ExpressionItem();
            BeanUtils.copyProperties(oi, vo);
            OperationItem.MeasureBasicInfo operand = oi.getOperand();
            if (operand != null) {
                MeasureBasicInfoVO mbVO = new MeasureBasicInfoVO();
                mbVO.setId(Integer.valueOf(operand.getId().toString()));
                mbVO.setCode(operand.getMeasCode());
                Measure measureServiceOne = measureService.getOne(Wrappers.<Measure>lambdaQuery().eq(Measure::getCode, operand.getMeasCode()));
                String measName = Optional.ofNullable(measureServiceOne).map(m -> m.getCnName()).orElse(operand.getMeasName());
                mbVO.setCnName(measName);
                vo.setOperand(mbVO);
            }
            expressionItemVOS.add(vo);
        });
        result.setExpressionItemList(expressionItemVOS);
        result.setModelId(measureApplication.getDwTableId());
        result.setWhereCondition(measureApplication.getWhereCondition());
        Integer dwTableId = measureApplication.getDwTableId();
        DwTable dwTable = dwTableMapper.selectById(dwTableId);
        if (dwTable != null) {
            result.setModelCnName(dwTable.getCnName());
            result.setModelEnName(dwTable.getEnName());
            result.setModelTableName(dwTable.getTableName());
        }

        List<MeasureNaturalDateMapping> measureNaturalDateMappings = naturalDateMappingService
                .list(Wrappers.<MeasureNaturalDateMapping>lambdaQuery()
                        .eq(MeasureNaturalDateMapping::getMeasId, measureApplication.getMeasId())
                        .eq(MeasureNaturalDateMapping::getDwTableId, measureApplication.getDwTableId()));
        List<NaturalDimConfigVO> configVOS = measureNaturalDateMappings.stream().map(mapping -> {
            Dimension dimension = dimensionService.getById(mapping.getTargetDimId());
            NaturalDimConfigVO dimConfigVO = new NaturalDimConfigVO();
            dimConfigVO.setDimId(mapping.getTargetDimId());
            dimConfigVO.setHyperDimId(mapping.getNaturalDimId());
            dimConfigVO.setMeasId(mapping.getMeasId());
            dimConfigVO.setModelId(mapping.getDwTableId());
            dimConfigVO.setDimCnName(dimension.getCnName());
            return dimConfigVO;
        }).collect(Collectors.toList());
        result.setNaturalDimConfig(configVOS);
        return result;
    }


    public LinkedList<DimensionFilterCreateVO> listDimensionFilters(Integer measAppId) {
        LinkedList<DimensionFilterCreateVO> result = new LinkedList<>();
        List<DimensionFilter> dimensionFilters = dimensionFilterMapper.selectList(Wrappers.<DimensionFilter>lambdaQuery().eq(DimensionFilter::getMeasAppId, measAppId));
        if (!CollectionUtils.isEmpty(dimensionFilters)) {
            dimensionFilters.forEach(df -> {
                DimensionFilterCreateVO vo = new DimensionFilterCreateVO();
                Dimension dimension = dimensionMapper.selectOne(Wrappers.<Dimension>lambdaQuery().eq(Dimension::getCode, df.getDimCode()));
                vo.setDimId(dimension.getId());
                vo.setEnName(dimension.getEnName());
                vo.setCnName(dimension.getCnName());
                vo.setDimCode(df.getDimCode());
                vo.setSqlLogicalType(df.getSqlLogicalType());
                LinkedList<DimensionFilterOperatorCreateVO> operatorList = new LinkedList<>();
                List<DimensionOperator> dimensionOperators = dimensionOperatorMapper.selectList(Wrappers.<DimensionOperator>lambdaQuery().eq(DimensionOperator::getFilterId, df.getId()));
                if (!CollectionUtils.isEmpty(dimensionOperators)) {
                    dimensionOperators.forEach(dimOper -> {
                        DimensionFilterOperatorCreateVO dimensionFilterOperatorCreateVO = new DimensionFilterOperatorCreateVO();
                        dimensionFilterOperatorCreateVO.setSqlOprType(dimOper.getSqlOprType());
                        dimensionFilterOperatorCreateVO.setSqlLogicalType(dimOper.getSqlLogicalType());
                        List<DimensionOperatorValue> values = dimensionOperatorValueService.list(Wrappers.<DimensionOperatorValue>lambdaQuery().eq(DimensionOperatorValue::getOperatorId, dimOper.getId()));
                        if (!CollectionUtils.isEmpty(values)) {
                            List<String> valueList = values.stream().map(DimensionOperatorValue::getValue).collect(Collectors.toList());
                            LinkedList<String> dataList = new LinkedList(valueList);
                            dimensionFilterOperatorCreateVO.setDataList(dataList);
                        }
                        operatorList.add(dimensionFilterOperatorCreateVO);
                    });
                }
                vo.setOperatorList(operatorList);
                result.add(vo);
            });
        }
        return result;
    }

    @Resource
    IAuthElementService authElementService;

    @Transactional(rollbackFor = Exception.class)
    public void deleteById(Integer id) {
        Measure measure = measureMapper.selectById(id);
        preDelete(measure);
        List<MeasureApplication> measureApplications = measureApplicationMapper.selectList(Wrappers.<MeasureApplication>lambdaQuery().eq(MeasureApplication::getMeasId, id));
        if (!CollectionUtils.isEmpty(measureApplications)) {
            Set<Integer> measAppIds = measureApplications.stream().map(MeasureApplication::getId).collect(Collectors.toSet());
            //指标的维度过滤器
            List<DimensionFilter> oldFilters = dimensionFilterService.list(Wrappers.<DimensionFilter>lambdaQuery().in(DimensionFilter::getMeasAppId, measAppIds));
            removeFilters(oldFilters);
            // 删除指标应用表
            measureApplicationMapper.deleteBatchIds(measAppIds);
        }
        // 删除指标
        measureMapper.deleteById(id);
        // 删除依赖树
        complexMeasureDependencyTreeService.remove(Wrappers.<ComplexMeasureDependencyTree>lambdaQuery().eq(ComplexMeasureDependencyTree::getComplexMeasId, id));
        // 删除指标-自然维度关联关系
        naturalDateMappingService.remove(Wrappers.<MeasureNaturalDateMapping>lambdaQuery().eq(MeasureNaturalDateMapping::getMeasId, id));
        // 删除授权表
        authElementService.remove(Wrappers.<AuthElement>lambdaQuery().eq(AuthElement::getCode, measure.getCode()));
    }

    private void removeFilters(List<DimensionFilter> filters) {
        if (!CollectionUtils.isEmpty(filters)) {
            // 查询旧数据
            List<Long> oldFilterIds = filters.stream().map(DimensionFilter::getId).collect(Collectors.toList());
            List<DimensionOperator> oldOperators = dimensionOperatorService.list(Wrappers.<DimensionOperator>lambdaQuery().in(DimensionOperator::getFilterId, oldFilterIds));
            List<Long> oldOperatorIds = oldOperators.stream().map(DimensionOperator::getId).collect(Collectors.toList());
            // 删除
            dimensionOperatorValueService.remove(Wrappers.<DimensionOperatorValue>lambdaQuery().in(DimensionOperatorValue::getOperatorId, oldOperatorIds));
            dimensionOperatorService.removeByIds(oldOperatorIds);
            dimensionFilterService.removeByIds(oldFilterIds);
        }
    }

    /**
     * 判断指标、维度是否被数据集引用
     *
     * @return
     */
    public boolean beCited(String code) {
        Long count = dataSourceService.getCountByDimCodeAndMeasCode(Arrays.asList(code));
        return count > 0;
    }

    /**
     * 判断指标是否只有一个表达式
     *
     * @param measId
     * @return
     */
    public boolean lastExpression(Integer measId) {
        List<MeasureApplication> list = measureApplicationService.list(Wrappers.<MeasureApplication>lambdaQuery().eq(MeasureApplication::getMeasId, measId).eq(MeasureApplication::getAvailable, YesNoType.YES));
        return list.size() == 1;
    }

    public void preDelete(Measure measure) {
        //TODO 1.是否被数据集引用
        Long count = dataSourceService.getCountByDimCodeAndMeasCode(Arrays.asList(measure.getCode()));
        if (count > 0) {
            throw IndicatorParamNotValidException.error("当前指标已经用于数据集，暂不支持删除，请先解除关系后再删除");
        }
        //2.是否存在依赖它的指标
        List<ComplexMeasureDependencyTree> dependencyTrees = complexMeasureDependencyTreeService.list(Wrappers.<ComplexMeasureDependencyTree>lambdaQuery()
                .eq(ComplexMeasureDependencyTree::getDependencyId, measure.getId())
                .eq(ComplexMeasureDependencyTree::getDependencyType, TableColumnType.MEASURE.getCode().intValue()));
        if (!CollectionUtils.isEmpty(dependencyTrees)) {
            Integer measureId = dependencyTrees.stream().map(ComplexMeasureDependencyTree::getComplexMeasId).findFirst().get();
            Measure dependencyMeasure = measureMapper.selectById(measureId);
            throw IndicatorParamNotValidException.error("当前指标是指标【" + dependencyMeasure.getCnName() + "】的依赖指标，删除后计算指标则不可用，请先解除依赖关系后，再来删除");
        }
    }

    public boolean measureNameRepeat(String enName, String cnName) {
        List<Measure> measures = measureMapper.selectList(Wrappers.<Measure>lambdaQuery()
                .eq(Measure::getEnName, enName)
                .or()
                .eq(Measure::getCnName, cnName));
        return !CollectionUtils.isEmpty(measures);
    }

    public Response updateOriginExpresionForExitsMeasure(MeasureExpUpdateVO measureExpUpdateVO, Measure measure) {
        MeasureApplication measureApplication = measureApplicationService.getById(measureExpUpdateVO.getMeasAppId());
        measureApplication.setApplyType(MeasureType.ORIGIN.getCode());
        measureApplication.setWhereCondition(measureExpUpdateVO.getWhereCondition());
        measureApplication.setMeasId(measureExpUpdateVO.getMeasureId());
        measureApplication.setFactColumn(measureExpUpdateVO.getColumnEnName());
        measureApplication.setDwTableId(measureExpUpdateVO.getModelId());
        measureApplication.setAvailable(YesNoType.YES.getCode());
        measureApplication.setExpression(buildOriginMeasureExpression(measureExpUpdateVO.getSqlAggFunType()));
        measureApplicationService.updateById(measureApplication);
        measureService.updateById(measure);
        String testSql = dorisQueryManager.runTest(dwTableMapper.selectById(measureApplication.getDwTableId()), Arrays.asList(measureApplication));
        OriginMeasureCreateResponseVO result = new OriginMeasureCreateResponseVO();
        result.setTestSql(testSql);
        return Response.ok(result);
    }


    @Transactional(rollbackFor = Exception.class)
    public Response createOriginExpresionForExitsMeasure(MeasureExpCreate originMeasureCreateVO, Measure measure) {
        MeasureApplication measureApplication = buildMeasureApplication(originMeasureCreateVO);
        measureApplicationMapper.insert(measureApplication);
        measureService.updateById(measure);
        String testSql = dorisQueryManager.runTest(dwTableMapper.selectById(measureApplication.getDwTableId()), Arrays.asList(measureApplication));
        OriginMeasureCreateResponseVO result = new OriginMeasureCreateResponseVO();
        result.setTestSql(testSql);
        return Response.ok(result);
    }

    private MeasureApplication buildMeasureApplication(MeasureExpCreate originMeasureCreateVO) {
        MeasureApplication measureApplication = new MeasureApplication();
        measureApplication.setApplyType(MeasureType.ORIGIN.getCode());
        measureApplication.setWhereCondition(originMeasureCreateVO.getWhereCondition());
        measureApplication.setMeasId(originMeasureCreateVO.getMeasureId());
        measureApplication.setFactColumn(originMeasureCreateVO.getColumnEnName());
        measureApplication.setDwTableId(originMeasureCreateVO.getModelId());
        measureApplication.setAvailable(YesNoType.YES.getCode());
        measureApplication.setExpression(buildOriginMeasureExpression(originMeasureCreateVO.getSqlAggFunType()));
        return measureApplication;
    }

    /**
     * 这是一个二合一的方法
     * 1.如果英文名重复，就会给现有指标增加一个新的表达式
     * 2.保存一个新的指标，
     *
     * @param originMeasureCreateVO
     */
    @Transactional(rollbackFor = Exception.class)
    public Response saveOrUpdateOriginMeasure(OriginMeasureCreateVO originMeasureCreateVO) {
        List<Measure> measureList = measureMapper.selectList(Wrappers.<Measure>lambdaQuery()
                .eq(Measure::getEnName, originMeasureCreateVO.getEnName())
                .eq(Measure::getCnName, originMeasureCreateVO.getCnName()));

        if (!CollectionUtils.isEmpty(measureList)) {
            // 英文名重复，说明是为现有指标创建新的表达式
            MeasureExpCreate measureExpCreate = new MeasureExpCreate();
            BeanUtils.copyProperties(originMeasureCreateVO, measureExpCreate);
            // 配置自然日期维度
            if (originMeasureCreateVO.getNaturalDimConfig() != null) {
                originMeasureCreateVO.getNaturalDimConfig().forEach(config -> {
                    if (config.getMeasId() == null) {
                        config.setMeasId(Long.valueOf(measureList.get(0).getId()));
                    }
                });
                naturalDimensionConfig(originMeasureCreateVO.getNaturalDimConfig());
            }
            return createOriginExpresionForExitsMeasure(measureExpCreate, measureList.get(0));
        } else {
            Measure measure = new Measure();
            BeanUtils.copyProperties(originMeasureCreateVO, measure);
            if (originMeasureCreateVO.getId() != null) {
                measure.initUpdate();
            } else {
                String code = measure.initCreateWithCodePrefix(IndicatorConstant.MEASURE_CODE_PREFIX);
                measure.setCode(code);
            }
            measureService.saveOrUpdate(measure);
            MeasureExpCreate measureExpCreate = new MeasureExpCreate();
            BeanUtils.copyProperties(originMeasureCreateVO, measureExpCreate);
            MeasureApplication measureApplication = buildMeasureApplication(measureExpCreate);
            measureApplication.setMeasId(measure.getId());
            measureApplicationService.saveOrUpdate(measureApplication, Wrappers.<MeasureApplication>lambdaQuery().eq(MeasureApplication::getId, originMeasureCreateVO.getMeasAppId()));
            //测试指标是否通过sql检查
            String runTestSql = dorisQueryManager.runTest(dwTableMapper.selectById(measureApplication.getDwTableId()), Arrays.asList(measureApplication));
            OriginMeasureCreateResponseVO result = new OriginMeasureCreateResponseVO();
            result.setTestSql(runTestSql);
            // 配置自然日期维度
            if (originMeasureCreateVO.getNaturalDimConfig() != null) {
                originMeasureCreateVO.getNaturalDimConfig().forEach(config -> {
                    if (config.getMeasId() == null) {
                        config.setMeasId(Long.valueOf(measure.getId()));
                    }
                });
                naturalDimensionConfig(originMeasureCreateVO.getNaturalDimConfig());
            }
            return Response.ok(result);
        }
    }


    private String buildOriginMeasureExpression(SqlAggFunType sqlAggFunType) {
        return JSON.toJSONString(Arrays.asList(OperationItemBuilder.originMeasureBuilde(sqlAggFunType)));
    }


    @Transactional(rollbackFor = Exception.class)
    public void updateComplexMeasure(ComplexMeasureUpdateVO complexMeasureUpdateVO) {
        // 检查中英文名是否与表中的其他字段重复重复
        List<Measure> measureList = measureMapper.selectList(Wrappers.<Measure>lambdaQuery().eq(Measure::getEnName, complexMeasureUpdateVO.getEnName()).or().eq(Measure::getCnName, complexMeasureUpdateVO.getCnName()));
        measureList.forEach(m -> {
            if (!Objects.equals(m.getId(), complexMeasureUpdateVO.getId())) {
                throw IndicatorParamNotValidException.error("指标中文名或者英文名已存在");
            }
        });
        LinkedList<ExpressionItem> expressionItemList = complexMeasureUpdateVO.getExpressionItemList();
        List<Integer> dependencyMeasureIds = new ArrayList<>();
        expressionItemList.forEach(i -> {
            if (ItemType.OPERAND.getName().equalsIgnoreCase(i.getOperatingType())) {
                MeasureBasicInfoVO operand = i.getOperand();
                dependencyMeasureIds.add(operand.getId());
            }
        });
        if (circularDependencyCheck(complexMeasureUpdateVO.getId(), dependencyMeasureIds)) {
            throw IndicatorParamNotValidException.error("指标修改存在循环依赖");
        }
        saveOrUpdateComplexMeasure(complexMeasureUpdateVO);
        if (!executeTest(complexMeasureUpdateVO.getId())) {
            throw IndicatorParamNotValidException.error("复合指标修改测试不通过,请检查依赖指标是否可用及表达式是否合法");
        }
    }

    /**
     * 检查指标是否存在循环依赖
     *
     * @return
     */
    public boolean circularDependencyCheck(MeasureExpBaseVO measureExpBaseVO) {
        LinkedList<ExpressionItem> expressionItemList = measureExpBaseVO.getExpressionItemList();
        List<Integer> dependencyMeasureIds = new ArrayList<>();
        expressionItemList.forEach(i -> {
            if (ItemType.OPERAND.getName().equalsIgnoreCase(i.getOperatingType())) {
                MeasureBasicInfoVO operand = i.getOperand();
                dependencyMeasureIds.add(operand.getId());
            }
        });
        if (circularDependencyCheck(measureExpBaseVO.getMeasureId(), dependencyMeasureIds)) {
            throw IndicatorParamNotValidException.error("指标修改存在循环依赖");
        }
        return false;
    }

    /**
     * 检查指标是否存在循环依赖
     *
     * @return
     */
    public boolean circularDependencyCheck(Integer targetMeasureId, List<Integer> dependencyMeasureIds) {
        if (CollectionUtils.isEmpty(dependencyMeasureIds)) {
            return false;
        }

        if (dependencyMeasureIds.contains(targetMeasureId)) {
            return true;
        }
        List<MeasureApplication> measureApplications = measureApplicationMapper.selectList(Wrappers.<MeasureApplication>lambdaQuery().in(MeasureApplication::getMeasId, dependencyMeasureIds));
        if (CollectionUtils.isEmpty(measureApplications)) {
            throw IndicatorParamNotValidException.error("依赖的指标不存在对应的模型");
        }

        for (MeasureApplication measureApplication : measureApplications) {
            Integer applyType = measureApplication.getApplyType();
            if (!Objects.equals(MeasureType.ORIGIN.getCode(), applyType)) {
                // 复合指标或派生指标，需要再次获取其依赖的原子指标
                String expression = measureApplication.getExpression();
                List<ExpressionItem> expressionItems = JSON.parseArray(expression, ExpressionItem.class);
                List<Integer> measIds = expressionItems.stream()
                        .filter(e -> ItemType.OPERAND.getName().equalsIgnoreCase(e.getOperatingType()))
                        .map(e -> e.getOperand().getId())
                        .collect(Collectors.toList());
                if (circularDependencyCheck(targetMeasureId, measIds)) {
                    return true;
                }
            }
        }
        return false;
    }

    @Autowired
    private IMeasureApplicationService measureApplicationService;
    @Autowired
    private IDimensionFilterService dimensionFilterService;
    @Autowired
    private IDimensionOperatorService dimensionOperatorService;

    /**
     * 保存计算指标的信息
     * 指标依赖树、维度过滤器
     *
     * @param complexMeasureVO
     * @param measureApplication
     */
    private void saveComplexMeasureInfo(ComplexMeasureBaseVO complexMeasureVO, MeasureApplication measureApplication) {
        List<ComplexMeasureDependencyTree> complexMeasureDependencyTrees = new ArrayList<>();
        LinkedList<ExpressionItem> expressionItemList = complexMeasureVO.getExpressionItemList();
        // 保存新数据
        LinkedList<DimensionFilterCreateVO> dimensionFilterList = complexMeasureVO.getDimensionFilterList();
        if (!CollectionUtils.isEmpty(dimensionFilterList)) {
            AtomicInteger seq = new AtomicInteger(0);
            dimensionFilterList.forEach(df -> {
                String dimCode = df.getDimCode();
                if (!dimensionManager.avaliable(dimCode)) {
                    throw IndicatorParamNotValidException.error("维度" + dimCode + "不可用");
                }
                // 保存依赖树
                ComplexMeasureDependencyTree tree = new ComplexMeasureDependencyTree();
                tree.setDependencyId(df.getDimId());
                tree.setDependencyType(TableColumnType.DIMENSION.getCode());
                tree.setMeasAppId(measureApplication.getId());
                tree.setComplexMeasId(complexMeasureVO.getId());
                complexMeasureDependencyTrees.add(tree);

                // save dimension_filter
                DimensionFilter dimensionFilter = new DimensionFilter();
                dimensionFilter.setMeasAppId(measureApplication.getId());
                dimensionFilter.setDimCode(dimCode);
                dimensionFilter.setSqlLogicalType(df.getSqlLogicalType());
                dimensionFilter.setSeq(seq.getAndIncrement());
                dimensionFilterService.saveOrUpdate(dimensionFilter);

                // save dimension_operator
                LinkedList<DimensionFilterOperatorCreateVO> operatorList = df.getOperatorList();
                AtomicInteger operSeq = new AtomicInteger(0);
                operatorList.forEach(o -> {
                    DimensionOperator dimensionOperator = new DimensionOperator();
                    dimensionOperator.setFilterId(dimensionFilter.getId());
                    BeanUtils.copyProperties(o, dimensionOperator);
                    dimensionOperator.setSeq(operSeq.getAndIncrement());
                    dimensionOperatorService.save(dimensionOperator);
                    LinkedList<String> dataList = o.getDataList();
                    if (!CollectionUtils.isEmpty(dataList)) {
                        List<DimensionOperatorValue> valueList = new LinkedList<>();
                        dataList.forEach(d -> {
                            DimensionOperatorValue dimensionOperatorValue = new DimensionOperatorValue();
                            dimensionOperatorValue.setOperatorId(dimensionOperator.getId());
                            dimensionOperatorValue.setValue(d);
                            valueList.add(dimensionOperatorValue);
                        });
                        // save operator
                        dimensionOperatorValueService.saveBatch(valueList);
                    }
                });
            });
        }

        // 保存新的依赖树
        expressionItemList.forEach(item -> {
            String operatingType = item.getOperatingType();
            if (ItemType.OPERAND.getName().equalsIgnoreCase(operatingType)) {
                Integer dependeMeasureId = item.getOperand().getId();
                List<MeasureApplication> measureApplications = measureApplicationMapper.selectList(Wrappers.<MeasureApplication>lambdaQuery().eq(MeasureApplication::getMeasId, dependeMeasureId));
                measureApplications.forEach(ma -> {
                    ComplexMeasureDependencyTree tree = new ComplexMeasureDependencyTree();
                    tree.setComplexMeasId(complexMeasureVO.getId());
                    tree.setMeasAppId(measureApplication.getId());
                    tree.setDependencyId(item.getOperand().getId());
                    tree.setDependencyType(TableColumnType.MEASURE.getCode());
                    tree.setDependencyMeasAppId(ma.getId());
                    complexMeasureDependencyTrees.add(tree);
                });
            }
        });
        complexMeasureDependencyTreeService.saveBatch(complexMeasureDependencyTrees);
    }


    public void updateComplexExpressionMeasure(ComplexMeasureUpdateVO complexMeasureVO) {
        Integer measAppId = complexMeasureVO.getMeasAppId();
        MeasureApplication measureApplication = measureApplicationService.getById(measAppId);
        measureApplication.initUpdate();
        measureApplication.setAvailable(AvailableType.AVAILABLE.getCode());
        LinkedList<ExpressionItem> expressionItemList = complexMeasureVO.getExpressionItemList();
        List<OperationItem> operationItems = expressionItemList.stream().map(e -> {
            OperationItem oi = new OperationItem();
            oi.setOperatingType(e.getOperatingType());
            oi.setConstant(e.getConstant());
            oi.setOperator(e.getOperator());
            MeasureBasicInfoVO operandVO = e.getOperand();
            if (operandVO != null) {
                OperationItem.MeasureBasicInfo operand = new OperationItem.MeasureBasicInfo(Long.valueOf(operandVO.getId()), operandVO.getCode(), operandVO.getCnName());
                oi.setOperand(operand);
            }
            return oi;
        }).collect(Collectors.toList());
        measureApplication.setExpression(JSON.toJSONString(operationItems));
        measureApplication.setApplyType(complexMeasureVO.getMeasureType());
        measureApplication.setMeasId(complexMeasureVO.getId());
        measureApplicationService.updateById(measureApplication);
        saveComplexMeasureInfo(complexMeasureVO, measureApplication);
        if (!executeTest(complexMeasureVO.getId())) {
            throw IndicatorParamNotValidException.error("复合指标创建测试不通过,请检查依赖指标是否可用及表达式是否合法");
        }
    }


    /**
     * 给现有的复合指标增加新的指标表达式
     *
     * @param complexMeasureVO
     */
    @Transactional(rollbackFor = Exception.class)
    public void createComplexExpressionMeasure(ComplexMeasureBaseVO complexMeasureVO) {
        MeasureApplication measureApplication = new MeasureApplication();
        measureApplication.initCreate();
        measureApplication.setAvailable(AvailableType.AVAILABLE.getCode());
        LinkedList<ExpressionItem> expressionItemList = complexMeasureVO.getExpressionItemList();
        List<OperationItem> operationItems = expressionItemList.stream().map(e -> {
            OperationItem oi = new OperationItem();
            oi.setOperatingType(e.getOperatingType());
            oi.setConstant(e.getConstant());
            oi.setOperator(e.getOperator());
            MeasureBasicInfoVO operandVO = e.getOperand();
            if (operandVO != null) {
                OperationItem.MeasureBasicInfo operand = new OperationItem.MeasureBasicInfo(Long.valueOf(operandVO.getId()), operandVO.getCode(), operandVO.getCnName());
                oi.setOperand(operand);
            }
            return oi;
        }).collect(Collectors.toList());
        measureApplication.setExpression(JSON.toJSONString(operationItems));
        measureApplication.setApplyType(complexMeasureVO.getMeasureType());
        measureApplication.setMeasId(complexMeasureVO.getId());
        measureApplicationService.save(measureApplication);
        saveComplexMeasureInfo(complexMeasureVO, measureApplication);
        if (!executeTest(complexMeasureVO.getId())) {
            throw IndicatorParamNotValidException.error("复合指标创建测试不通过,请检查依赖指标是否可用及表达式是否合法");
        }
    }


    /**
     * 复合/派生指标关联的表很多，更新或删除方法采用的策略是先删除旧数据，再写入新数据
     *
     * @param complexMeasureVO
     */
    private void saveOrUpdateComplexMeasure(ComplexMeasureBaseVO complexMeasureVO) {
        // 指标和指标应用表采用更新或插入策略
        Measure measure = new Measure();
        String code = measure.initCreateWithCodePrefix(IndicatorConstant.MEASURE_CODE_PREFIX);
        measure.setCode(code);
        BeanUtils.copyProperties(complexMeasureVO, measure);
        measureService.saveOrUpdate(measure);

        MeasureApplication measureApplication = new MeasureApplication();
        Integer measAppId = complexMeasureVO.getMeasAppId();
        if (Objects.isNull(measAppId)) {
            measureApplication.initCreate();
            measureApplication.setMeasId(measAppId);
        } else {
            measureApplication.initUpdate();
        }
        measureApplication.setAvailable(AvailableType.AVAILABLE.getCode());
        LinkedList<ExpressionItem> expressionItemList = complexMeasureVO.getExpressionItemList();
        List<OperationItem> operationItems = expressionItemList.stream().map(e -> {
            OperationItem oi = new OperationItem();
            oi.setOperatingType(e.getOperatingType());
            oi.setConstant(e.getConstant());
            oi.setOperator(e.getOperator());
            MeasureBasicInfoVO operandVO = e.getOperand();
            if (operandVO != null) {
                OperationItem.MeasureBasicInfo operand = new OperationItem.MeasureBasicInfo(Long.valueOf(operandVO.getId()), operandVO.getCode(), operandVO.getCnName());
                oi.setOperand(operand);
            }
            return oi;
        }).collect(Collectors.toList());
        measureApplication.setExpression(JSON.toJSONString(operationItems));
        measureApplication.setApplyType(complexMeasureVO.getMeasureType());
        measureApplication.setMeasId(measure.getId());
        measureApplication.setWhereCondition(complexMeasureVO.getWhereCondition());
        measureApplicationService.saveOrUpdate(measureApplication, Wrappers.<MeasureApplication>lambdaQuery().eq(MeasureApplication::getId, measureApplication.getId()));
        final MeasureApplication newMeasureApplication = measureApplicationService.getOne(Wrappers.<MeasureApplication>lambdaQuery().eq(MeasureApplication::getMeasId, measure.getId()));

        // 维度筛选器和sql操作采用先删除再保存策略
        List<DimensionFilter> oldFilters = dimensionFilterService.list(Wrappers.<DimensionFilter>lambdaQuery().eq(DimensionFilter::getMeasAppId, newMeasureApplication.getId()));
        removeFilters(oldFilters);
        // 删除指标依赖树
        complexMeasureDependencyTreeService.remove(Wrappers.<ComplexMeasureDependencyTree>lambdaQuery().eq(ComplexMeasureDependencyTree::getMeasAppId, newMeasureApplication.getId()));
        // 保存新数据
        complexMeasureVO.setId(newMeasureApplication.getMeasId());
        saveComplexMeasureInfo(complexMeasureVO, newMeasureApplication);
        if (!executeTest(measure.getId())) {
            throw IndicatorParamNotValidException.error("复合指标创建测试不通过,请检查依赖指标是否可用及表达式是否合法");
        }
    }

    @Transactional(rollbackFor = Exception.class)
    public void createComplexMeasure(ComplexMeasureCreateVO complexMeasureCreateVO) {
        List<Measure> measureList = measureMapper.selectList(Wrappers.<Measure>lambdaQuery().eq(Measure::getEnName, complexMeasureCreateVO.getEnName()).eq(Measure::getCnName, complexMeasureCreateVO.getCnName()));
        if (!CollectionUtils.isEmpty(measureList)) {
            //现有计算指标增加新的加工方式
            Measure measure = measureList.get(0);
            complexMeasureCreateVO.setId(measure.getId());
            createComplexExpressionMeasure(complexMeasureCreateVO);
        } else {
            // 如果指标中英文名不存在，一定是创建新的计算指标
            saveOrUpdateComplexMeasure(complexMeasureCreateVO);
        }
        if (!executeTest(complexMeasureCreateVO.getId())) {
            throw IndicatorParamNotValidException.error("复合指标创建测试不通过,请检查依赖指标是否可用及表达式是否合法");
        }
    }


    // @Transactional(rollbackFor = Exception.class)
    // public void createComplexMeasure(ComplexMeasureCreateVO complexMeasureCreateVO){
    //     Measure measure = new Measure();
    //     String code = measure.initCreateWithCodePrefix(IndicatorConstant.MEASURE_CODE_PREFIX);
    //     measure.setCode(code);
    //     BeanUtils.copyProperties(complexMeasureCreateVO,measure);
    //     measureMapper.insert(measure);
    //
    //     MeasureApplication measureApplication = new MeasureApplication();
    //     measureApplication.initCreate();
    //     measureApplication.setAvailable(AvailableType.AVAILABLE.getCode());
    //     List<ExpressionItem> expressionItemList = complexMeasureCreateVO.getExpressionItemList();
    //     measureApplication.setExpression(JSON.toJSONString(expressionItemList));
    //     measureApplication.setApplyType(complexMeasureCreateVO.getApplyType());
    //     measureApplication.setMeasId(measure.getId());
    //     measureApplicationMapper.insert(measureApplication);
    //
    //     LinkedList<DimensionFilterCreateVO> dimensionFilterList = complexMeasureCreateVO.getDimensionFilterList();
    //     if (!CollectionUtils.isEmpty(dimensionFilterList)){
    //         dimensionFilterList.forEach(df -> {
    //             String dimCode = df.getDimCode();
    //             if (!dimensionManager.avaliable(dimCode)){
    //                 throw IndicatorParamNotValidException.error("维度" + dimCode + "不可用");
    //             }
    //             // save dimension_filter
    //             DimensionFilter dimensionFilter = new DimensionFilter();
    //             dimensionFilter.setMeasAppId(measureApplication.getId());
    //             dimensionFilter.setDimCode(dimCode);
    //             dimensionFilter.setSqlLogicalType(df.getSqlLogicalType());
    //             dimensionFilterMapper.insert(dimensionFilter);
    //
    //             // save dimension_operator
    //             LinkedList<DimensionFilterOperatorCreateVO> operatorList = df.getOperatorList();
    //             operatorList.forEach(o -> {
    //                 DimensionOperator dimensionOperator = new DimensionOperator();
    //                 dimensionOperator.setFilterId(dimensionFilter.getId());
    //                 BeanUtils.copyProperties(o,dimensionOperator);
    //                 dimensionOperatorMapper.insert(dimensionOperator);
    //                 LinkedList<String> dataList = o.getDataList();
    //                 if (!CollectionUtils.isEmpty(dataList)){
    //                     List<DimensionOperatorValue> valueList = new LinkedList<>();
    //                     dataList.forEach(d -> {
    //                         DimensionOperatorValue dimensionOperatorValue = new DimensionOperatorValue();
    //                         dimensionOperatorValue.setOperatorId(dimensionOperator.getId());
    //                         dimensionOperatorValue.setValue(d);
    //                         valueList.add(dimensionOperatorValue);
    //                     });
    //                     // save operator
    //                     dimensionOperatorValueService.saveBatch(valueList);
    //                 }
    //             });
    //         });
    //     }
    //
    //     if(! executeTest(measure.getId())){
    //         throw IndicatorParamNotValidException.error("复合指标创建测试不通过,请检查依赖指标是否可用及表达式是否合法");
    //     }
    // }

    /**
     * 测试指标查询是否成功
     * select xxx from xxx limit 0 执行成功
     *
     * @return TODO
     */
    public boolean executeTest(Integer measId) {

        return true;
    }


    /**
     * 检查指标是否可用
     * 可用条件：
     * 1.原子指标，只要本身在线即可
     * 2.复合指标，自身在线且依赖的指标也在线
     * 3.派生指标，自身在线，依赖的指标和维度也在线
     *
     * @return
     */
    public boolean available(Integer measId) {

        Measure measure = measureMapper.selectById(measId);
        if (measure == null) {
            return false;
        }
        Integer online = measure.getOnline();
        Integer isDelete = measure.getIsDelete();

        // 是否在线和被删除
        if (Objects.equals(YesNoType.NO.getCode(), online) || Objects.equals(YesNoType.YES.getCode(), isDelete)) {
            return false;
        }

        List<MeasureApplication> measureApplications = listAvaliableMeasApplication(measId);
        if (CollectionUtils.isEmpty(measureApplications)) {
            return false;
        }

        // 有多个关联的模型，只有有一个模型可用，指标就可用
        for (MeasureApplication measureApplication : measureApplications) {
            if (measureApplicationAvaliable(measureApplication)) {
                return true;
            }
        }
        return false;
    }


    /**
     * 判断指标所在事实表是否可用
     * 可用条件：
     * 1.原子指标，直接可用
     * 2.复合指标，依赖的指标在线
     * 3.派生指标，依赖的指标和维度同时在线
     *
     * @return
     */
    public boolean measureApplicationAvaliable(MeasureApplication measureApplication) {
        String expression = measureApplication.getExpression();
        List<ExpressionItem> expressionItems = JSON.parseArray(expression, ExpressionItem.class);
        List<Integer> measIds = expressionItems.stream()
                .filter(e -> ItemType.OPERAND.getName().equalsIgnoreCase(e.getOperatingType()))
                .map(e -> e.getOperand().getId())
                .collect(Collectors.toList());

        if (!CollectionUtils.isEmpty(measIds)) {
            // 不为空说明有依赖的指标，需要判断依赖指标的状态是否全部在线
            List<Measure> measures = listUnavaliableMeasure(measIds);
            if (!CollectionUtils.isEmpty(measures)) {
                // 不为空说明有下线的指标
                return false;
            }
        }
        // TODO 判断依赖的维度是否在线

        return true;
    }


    /**
     * 获取下线状态的指标列表
     *
     * @param measIds
     * @return
     */
    public List<Measure> listUnavaliableMeasure(List<Integer> measIds) {
        if (CollectionUtils.isEmpty(measIds)) {
            return Collections.EMPTY_LIST;
        }
        return measureMapper.selectList(Wrappers.<Measure>lambdaQuery()
                .in(Measure::getId, measIds)
                .eq(Measure::getOnline, YesNoType.NO.getCode()));
    }

    // /**
    //  * 获取指标依赖的指标
    //  * 原子指标依赖的指标为空
    //  * @return
    //  */
    // public List<Measure> listRelyMeasureByMeasId(Integer measId){
    //     Measure measure = measureMapper.selectById(measId);
    //
    //     if (Objects.equals(MeasureType.DERIVED.getCode(),measure.getType())){
    //         // 复合指标
    //         List<MeasureApplication> measureApplications = measureApplicationMapper.selectList(Wrappers.<MeasureApplication>lambdaQuery()
    //                 .eq(MeasureApplication::getMeasId, measId)
    //                 .eq(MeasureApplication::getAvailable, YesNoType.YES.getCode()));
    //
    //         Set<Integer> allMeasIds = new HashSet<>();
    //         allMeasIds.add(-1);//避免ID为空SQL报错
    //         measureApplications.forEach(ma -> {
    //             String expression = ma.getExpression();
    //             List<ExpressionItem> expressionItems = JSON.parseArray(expression, ExpressionItem.class);
    //             Set<Integer> measIds = expressionItems.stream()
    //                     .filter(e -> ItemType.OPERAND.getName().equalsIgnoreCase(e.getOperatingType()))
    //                     .map(e -> e.getOperand().getId())
    //                     .collect(Collectors.toSet());
    //             allMeasIds.addAll(measIds);
    //         });
    //         return measureMapper.selectBatchIds(allMeasIds);
    //
    //     } else if(Objects.equals(MeasureType.EXTENDED.getCode(),measure.getType())) {
    //         // TODO 派生指标
    //         return null;
    //     } else {
    //         // 原子指标
    //         return Collections.EMPTY_LIST;
    //     }
    // }

    /**
     * 判断根据原子指标是否有可用的模型
     * 模型可用条件：有对应的模型字段，且字段在线
     *
     * @return
     */
    public boolean hasAvaliableModel(Integer originMeasId) {
        List<MeasureApplication> measureApplications = measureApplicationMapper.selectList(Wrappers.<MeasureApplication>lambdaQuery()
                .eq(MeasureApplication::getMeasId, originMeasId)
                .eq(MeasureApplication::getAvailable, YesNoType.YES.getCode()));

        return !CollectionUtils.isEmpty(measureApplications);
    }

    /**
     * 获取指标可用的应用表
     * 可用条件：字段在线
     *
     * @param measId
     * @return
     */
    public List<MeasureApplication> listAvaliableMeasApplication(Integer measId) {
        return measureApplicationMapper.selectList(Wrappers.<MeasureApplication>lambdaQuery()
                .eq(MeasureApplication::getMeasId, measId)
                .eq(MeasureApplication::getAvailable, YesNoType.YES.getCode()));
    }

    @Autowired
    CacheManager cacheManager;

    public List<Dimension> fetchRelatedDimensionFromCache(Integer measureId) {
        MeasureCache measureCache = cacheManager.getMeasureCache(measureId);
        if (Objects.isNull(measureCache)) {
            return Collections.EMPTY_LIST;
        }
        Set<Integer> relatedDimensionIds = measureCache.getRelatedDimensionIds();
        if (CollectionUtils.isEmpty(relatedDimensionIds)) {
            return Collections.EMPTY_LIST;
        }
        return dimensionMapper.selectBatchIds(relatedDimensionIds);
    }

    public Set<Integer> getDimensionByMeas(Integer measureId) {
        MeasureCache measureCache = cacheManager.getMeasureCache(measureId);
        if (Objects.isNull(measureCache)) {
            return Collections.EMPTY_SET;
        }
        Set<Integer> relatedDimensionIds = measureCache.getRelatedDimensionIds();
        return relatedDimensionIds;
    }


    /**
     * 从DB中获取指标的相关维度
     * 如果指标有多个表达式比如，既是原子指标，又是计算指标，那么它的相关维度是
     * key: 指标表达式ID measAppId
     * value: 指标相关维度
     *
     * @param measureId
     * @return
     */
    public Map<Integer, Set<Integer>> fetchRelatedDimensionFromDB(Integer measureId) {
        Map<Integer, Set<Integer>> result = new HashMap<>();
        List<MeasureApplication> measureApplications = measureApplicationService.list(Wrappers.<MeasureApplication>lambdaQuery().eq(MeasureApplication::getMeasId, measureId));
        if (CollectionUtils.isEmpty(measureApplications)) {
            return result;
        }
        measureApplications.forEach(ma -> {
            if (Objects.equals(ma.getApplyType(), MeasureType.ORIGIN.getCode())) {
                // 原子指标
                result.put(ma.getId(), listOriginMeasureRelatedDimensionId(ma));
            } else if (Objects.equals(ma.getApplyType(), MeasureType.DERIVED.getCode()) || Objects.equals(ma.getApplyType(), MeasureType.EXTENDED.getCode())) {
                // 复合指标、派生指标
                result.put(ma.getId(), listComplexMeasureRelatedDimensionId(ma));
            }
        });
        return result;
    }

    /**
     * 获取一个计算指标表达式所依赖的基础指标
     *
     * @return
     */
    private void listComplexMeasureDependencyBaseMeasure(Integer complexMeasAppId, List<MeasureApplication> originMeasApps) {
        List<List<Integer>> result = new ArrayList<>();
        List<ComplexMeasureDependencyTree> measureDependencyTrees = complexMeasureDependencyTreeService.list(Wrappers.<ComplexMeasureDependencyTree>lambdaQuery()
                .eq(ComplexMeasureDependencyTree::getMeasAppId, complexMeasAppId)
                .eq(ComplexMeasureDependencyTree::getDependencyType, TableColumnType.MEASURE.getCode()));
        if (!CollectionUtils.isEmpty(measureDependencyTrees)) {
            for (ComplexMeasureDependencyTree cmdt : measureDependencyTrees) {
                List<Integer> dependencyMeasAppIds = new ArrayList<>();
                Integer measId = cmdt.getDependencyId();
                List<MeasureApplication> measureApplications = measureApplicationService.list(Wrappers.<MeasureApplication>lambdaQuery().eq(MeasureApplication::getMeasId, measId));
                if (!CollectionUtils.isEmpty(measureApplications)) {
                    // 依赖的指标有多个表达式
                    measureApplications.forEach(ma -> {
                        if (Objects.equals(ma.getApplyType(), MeasureType.ORIGIN.getCode())) {
                            dependencyMeasAppIds.add(ma.getId());
                        } else {
                            listComplexMeasureDependencyBaseMeasure(ma.getId(), originMeasApps);
                        }
                    });
                } else {
                    log.error("指标依赖树所依赖的指标对应的指标应用表不存在,请检查数据,dependencyInfo:{}", JSON.toJSONString(cmdt));
                }
                result.add(dependencyMeasAppIds);
            }
        }
    }

    /**
     * 获取计算指标的相关维度(包含级联)
     *
     * @return
     */
    private Set<Integer> listComplexMeasureRelatedDimensionId(MeasureApplication measureApplication) {
        // 获取计算指标依赖的原子指标
        List<MeasureApplication> originMeasApps = new ArrayList<>();
        listComplexMeasureDependencyBaseMeasure(measureApplication.getId(), originMeasApps);
        if (CollectionUtils.isEmpty(originMeasApps)) {
            return Collections.EMPTY_SET;
        }
        Set<Integer> result = listOriginMeasureRelatedDimensionId(originMeasApps.get(0));
        for (int i = 1; i < originMeasApps.size(); i++) {
            Set<Integer> relatedDimensionId = listOriginMeasureRelatedDimensionId(originMeasApps.get(i));
            result.retainAll(relatedDimensionId);
        }
        return result;
    }


    /**
     * 获取原子指标的相关维度(包含级联)
     *
     * @param measureApplication
     * @return
     */
    private Set<Integer> listOriginMeasureRelatedDimensionId(MeasureApplication measureApplication) {
        if (measureApplication == null || measureApplication.getDwTableId() == null) {
            return Collections.EMPTY_SET;
        }
        Integer dwTableId = measureApplication.getDwTableId();
        List<DimensionApplication> dimensionApplications = dimensionApplicationMapper.selectList(Wrappers.<DimensionApplication>lambdaQuery().eq(DimensionApplication::getDwTableId, dwTableId));
        if (CollectionUtils.isEmpty(dimensionApplications)) {
            return Collections.EMPTY_SET;
        }
        Set<Integer> result = new HashSet<>();
        Set<Integer> dimIds = dimensionApplications.stream().map(DimensionApplication::getDimId).collect(Collectors.toSet());
        dimIds.forEach(dimId -> {
            List<Dimension> dimensionList = dimensionManager.listGeSeqDimensions(dimId);
            Set<Integer> set = dimensionList.stream().map(Dimension::getId).collect(Collectors.toSet());
            result.addAll(set);
        });
        return result;
    }

    public List<ModelVO> fetchRelatedModelFromCache(Integer measureId) {
        List<ModelVO> result = new ArrayList<>();
        MeasureCache measureCache = cacheManager.getMeasureCache(measureId);
        if (Objects.isNull(measureCache)) {
            return result;
        }
        MetadataCache metadataCache = cacheManager.getMetadataCache();
        Map<Integer, DwTable> dwTableMap = metadataCache.getDwTableMap();
        List<MeasureApplicationCache> measureApplicationCacheList = measureCache.getMeasureApplicationCacheList();


        if (!CollectionUtils.isEmpty(measureApplicationCacheList)) {
            Map<Integer, List<MeasureApplicationCache>> listMap = measureApplicationCacheList.stream().collect(Collectors.groupingBy(MeasureApplicationCache::getRelatedDwTableId));
            listMap.forEach((k, v) -> {
                DwTable dwTable = dwTableMap.get(k);
                if (Objects.nonNull(dwTable)) {
                    ModelVO modelVO = modelManager.getModelVOByTable(dwTable);
                    List<ComplexMeasureBaseVO> complexMeasureBaseVOS = new ArrayList<>();
                    v.forEach(mac -> {
                        BeanUtils.copyProperties(dwTable, modelVO);
                        ComplexMeasureBaseVO complexMeasureBaseVO = getExpresstion(mac.getMeasAppId());
                        complexMeasureBaseVOS.add(complexMeasureBaseVO);
                    });
                    modelVO.setMeasureExpressions(complexMeasureBaseVOS);
                    result.add(modelVO);
                }
            });
        }
        return result;
    }

    @Autowired
    private ModelManager modelManager;

    /**
     * // List<ComplexMeasureVO> complexMeasureVOList = new ArrayList<>();
     * // measureApplicationCacheList.forEach(mac -> {
     * //     ComplexMeasureVO complexMeasureVO =  new ComplexMeasureVO();
     * //     complexMeasureVO.setApplyType(mac.getApplyType());
     * //     complexMeasureVO.setMeasAppId(mac.getMeasAppId());
     * //     MeasureApplication measureApplication = measureApplicationMap.get(mac.getMeasAppId());
     * //     String expression = measureApplication.getExpression();
     * //     List<ExpressionItemVO> expressionItemVOList = JSON.parseArray(expression, ExpressionItemVO.class);
     * //     LinkedList<ExpressionItemVO> items = new LinkedList(expressionItemVOList);
     * //     complexMeasureVO.setExpressionItemList(items);
     * //     List<Filter> filterList = measAppIdFiltersMap.get(mac.getMeasAppId());
     * //     List<DimensionFilterCreateVO> dimensionFilterCreateVOS = filterList.stream().map(f -> {
     * //         DimensionFilterCreateVO dimensionFilterCreateVO = new DimensionFilterCreateVO();
     * //         dimensionFilterCreateVO.setDimCode(f.getCode());
     * //         dimensionFilterCreateVO.setSqlLogicalType(f.getSqlLogicalType().getCode());
     * //         List<Operator> operatorList = f.getOperatorList();
     * //         List<DimensionFilterOperatorCreateVO> dimensionFilterOperatorCreateVOS = operatorList.stream().map(operator -> {
     * //             DimensionFilterOperatorCreateVO dimensionFilterOperatorCreateVO = new DimensionFilterOperatorCreateVO();
     * //             dimensionFilterOperatorCreateVO.setSqlLogicalType(operator.getSqlLogicalType().getCode());
     * //             dimensionFilterOperatorCreateVO.setSqlOprType(operator.getSqlOprType().getCode());
     * //             dimensionFilterOperatorCreateVO.setDataList(new LinkedList<>(operator.getDataList()));
     * //             return dimensionFilterOperatorCreateVO;
     * //         }).collect(Collectors.toList());
     * //         dimensionFilterCreateVO.setOperatorList(new LinkedList<>(dimensionFilterOperatorCreateVOS));
     * //         return dimensionFilterCreateVO;
     * //     }).collect(Collectors.toList());
     * //     complexMeasureVO.setDimensionFilterList(new LinkedList<>(dimensionFilterCreateVOS));
     * //     complexMeasureVOList.add(complexMeasureVO);
     * // });
     *
     * @param measureVO
     * @return
     */


    @Transactional(rollbackFor = Exception.class)
    public boolean create(MeasureCreateVO measureVO) {
        Measure measure = new Measure();
        BeanUtils.copyProperties(measureVO, measure);
        String code = measure.initCreateWithCodePrefix(IndicatorConstant.MEASURE_CODE_PREFIX);
        measure.setCode(code);
        measureService.save(measure);
        return true;
    }


    @Transactional(rollbackFor = Exception.class)
    public void update(MeasureUpdateVO measureUpdateVO, User user) {
        Measure measure = new Measure();
        measure.initUpdate();
        BeanUtils.copyProperties(measureUpdateVO, measure);
        measure.setDepartmentId(measureUpdateVO.getDepartment() == null || measureUpdateVO.getDepartment().getDepartmentId() == null ? IndicatorConstant.ANONYMOUS_DEPT : measureUpdateVO.getDepartment().getDepartmentId());
        measureService.updateById(measure);

        wordValuesMapper.delAliase(measureUpdateVO.getCnName());
        if (!measureUpdateVO.getAliases().isEmpty()) {
            wordValuesMapper.insertAliase(measureUpdateVO.getAliases(), measureUpdateVO.getCnName());
        }

    }

}
