package com.graphinsight.indicator.manager;

import com.graphinsight.indicator.util.SqlInjectionUtils;
import com.baomidou.dynamic.datasource.annotation.DS;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.graphinsight.indicator.annotation.CheckCacheVersion;
import com.graphinsight.indicator.auto.entity.ComplexMeasureDependencyTree;
import com.graphinsight.indicator.auto.entity.Dashboard;
import com.graphinsight.indicator.auto.entity.DataSource;
import com.graphinsight.indicator.auto.entity.Dimension;
import com.graphinsight.indicator.auto.entity.DimensionApplication;
import com.graphinsight.indicator.auto.entity.DimensionDimtableConnect;
import com.graphinsight.indicator.auto.entity.DimensionValues;
import com.graphinsight.indicator.auto.entity.DwTable;
import com.graphinsight.indicator.auto.entity.Hierarchy;
import com.graphinsight.indicator.auto.entity.Level;
import com.graphinsight.indicator.auto.entity.Measure;
import com.graphinsight.indicator.auto.entity.MeasureNaturalDateMapping;
import com.graphinsight.indicator.auto.entity.User;
import com.graphinsight.indicator.auto.entity.Widget;
import com.graphinsight.indicator.auto.mapper.CategoryMapper;
import com.graphinsight.indicator.auto.mapper.DimensionApplicationMapper;
import com.graphinsight.indicator.auto.mapper.DwTableMapper;
import com.graphinsight.indicator.auto.mapper.HierarchyMapper;
import com.graphinsight.indicator.auto.mapper.LevelMapper;
import com.graphinsight.indicator.auto.mapper.MeasureApplicationMapper;
import com.graphinsight.indicator.auto.mapper.MeasureMapper;
import com.graphinsight.indicator.auto.service.IComplexMeasureDependencyTreeService;
import com.graphinsight.indicator.auto.service.IDashboardService;
import com.graphinsight.indicator.auto.service.IDimensionApplicationService;
import com.graphinsight.indicator.auto.service.IDimensionDimtableConnectService;
import com.graphinsight.indicator.auto.service.IDimensionService;
import com.graphinsight.indicator.auto.service.IDimensionValuesService;
import com.graphinsight.indicator.auto.service.ILevelService;
import com.graphinsight.indicator.auto.service.IMeasureNaturalDateMappingService;
import com.graphinsight.indicator.constant.IndicatorConstant;
import com.graphinsight.indicator.enums.DimFrontType;
import com.graphinsight.indicator.enums.DimType;
import com.graphinsight.indicator.enums.FieldType;
import com.graphinsight.indicator.enums.TableColumnType;
import com.graphinsight.indicator.enums.ViewType;
import com.graphinsight.indicator.enums.YesNoType;
import com.graphinsight.indicator.exception.IndicatorParamNotValidException;
import com.graphinsight.indicator.model.ReferenceCheck;
import com.graphinsight.indicator.model.cache.DimensionCache;
import com.graphinsight.indicator.model.cache.DwTableCache;
import com.graphinsight.indicator.model.dto.ColumnCheckResult;
import com.graphinsight.indicator.model.dto.IndicatorBean;
import com.graphinsight.indicator.model.dto.RelatedResourceDTO;
import com.graphinsight.indicator.model.vo.CategoryVO;
import com.graphinsight.indicator.model.vo.DimensionApplicationVO;
import com.graphinsight.indicator.model.vo.DimensionCreateVO;
import com.graphinsight.indicator.model.vo.DimensionUpdateVO;
import com.graphinsight.indicator.model.vo.DimensionVO;
import com.graphinsight.indicator.model.vo.DimensionValueItem;
import com.graphinsight.indicator.model.vo.DimensionValuesCreateVO;
import com.graphinsight.indicator.model.vo.LevelVO;
import com.graphinsight.indicator.model.vo.OfflineRequest;
import com.graphinsight.indicator.service.DataSourceService;
import com.graphinsight.indicator.service.DimensionQueryService;
import com.graphinsight.indicator.service.RedisCacheService;
import com.graphinsight.indicator.service.impl.BuildSqlServiceImpl;
import com.graphinsight.indicator.service.impl.MeasureMonitorReferenceServiceImpl;
import com.graphinsight.indicator.util.IndicatorAssert;
import com.graphinsight.indicator.util.UserThreadLocalUtil;
import com.graphinsight.indicator.util.sql.CheckWhereUtil;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import javax.annotation.Resource;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * @Description: 维度管理类
 * @Date: 2021/11/17
 */
@Service
@DS("mysql")
public class DimensionManager {

    @Autowired
    IDimensionService dimensionService;
    @Autowired
    private HierarchyMapper hierarchyMapper;

    @Autowired
    private IDimensionDimtableConnectService dimensionDimtableConnectService;

    @Autowired
    MeasureMapper measureMapper;
    @Autowired
    LevelMapper levelMapper;
    @Autowired
    private DimensionApplicationMapper dimensionApplicationMapper;
    @Autowired
    private DwTableMapper dwTableMapper;
    @Autowired
    private MeasureApplicationMapper measureApplicationMapper;
    @Autowired
    private IDimensionValuesService dimensionValuesService;
    @Autowired
    private IComplexMeasureDependencyTreeService complexMeasureDependencyTreeService;
    @Autowired
    private DimensionQueryService dimensionQueryService;
    @Autowired
    CategoryMapper categoryMapper;
    @Autowired
    UserManager userManager;
    @Autowired
    CategoryManager categoryManager;

    @Autowired
    IDimensionApplicationService dimensionApplicationService;
    @Resource
    IDashboardService dashboardService;

    public List<ColumnCheckResult> checkDimensionTable() {
        List<Dimension> dimensions = dimensionService.list(Wrappers.<Dimension>lambdaQuery().eq(Dimension::getDimType, DimType.STD_WITH_TABLE.getValue()));
        List<DimensionDimtableConnect> dimensionDimtableConnects = dimensionDimtableConnectService.list();
        Map<Integer, List<DimensionDimtableConnect>> dimConnectMap = dimensionDimtableConnects.stream().collect(Collectors.groupingBy(DimensionDimtableConnect::getDimId));
        List<ColumnCheckResult> result = new ArrayList<>();
        for (Dimension dimension : dimensions) {
            List<DimensionDimtableConnect> list = dimConnectMap.get(dimension.getId());
            if (CollectionUtils.isEmpty(list)) {
                ColumnCheckResult columnCheckResult = new ColumnCheckResult();
                columnCheckResult.setCnName(dimension.getCnName());
                columnCheckResult.setMessage("标准维对应的维表为空");
                result.add(columnCheckResult);
            }
        }
        return result;
    }

    @Resource
    ReferenceManager referenceManager;


    public List<ReferenceCheck> offline(OfflineRequest request) {
        List<ReferenceCheck> checks = offlineCheck(request);
        Integer id = request.getId();
        Dimension dimension = dimensionService.getById(id);
        IndicatorAssert.indicatorAssert(dimension == null, "指标不存在,ID:" + id);
        if (CollectionUtils.isEmpty(checks)) {
            // 检查通过再更新
            dimension.setOnline(YesNoType.NO.getCode());
            dimension.setOfflineRemark(request.getReason());
            dimension.setOfflineOperator(UserThreadLocalUtil.getUserName());
            dimension.setOfflineTime(LocalDateTime.now());
            dimensionService.updateById(dimension);
        }
        return checks;
    }


    public void online(Integer id) {
        Dimension dimension = dimensionService.getById(id);
        IndicatorAssert.indicatorAssert(dimension == null, "指标不存在,ID:" + id);
        dimension.setOnline(YesNoType.YES.getCode());
        dimensionService.updateById(dimension);
    }


    public List<ReferenceCheck> offlineCheck(OfflineRequest request) {
        Integer id = request.getId();
        Dimension dimension = dimensionService.getById(id);
        IndicatorAssert.indicatorAssert(dimension == null, "维度不存在,ID:" + id);
        List<Dimension> checkList = new ArrayList<>();
        // 查询下游指标
        List<ReferenceCheck> checks = new ArrayList<>();
        IndicatorBean bean = new IndicatorBean();
        bean.setType(FieldType.DIMENSION);
        bean.setCode(dimension.getCode());
        List<RelatedResourceDTO> relatedResourceDTOS = referenceManager.listRelatedResource(bean);
        if (!CollectionUtils.isEmpty(relatedResourceDTOS)) {
            ReferenceCheck check = new ReferenceCheck();
            check.setDimension(dimension);
            check.setRelatedResourceDTOList(relatedResourceDTOS);
            checks.add(check);
        }
        return checks;
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
        DimensionApplication application = dimensionApplicationService.getById(expressionId);
        DimensionCache cache = cacheManager.getDimensionCache(application.getDimId());
        if (cache == null) {
            return Collections.EMPTY_LIST;
        }
        Set<Integer> dwTableIds = cache.getRelatedDwTableIds();
        if (CollectionUtils.isEmpty(dwTableIds)) {
            return Collections.EMPTY_LIST;
        }

        List<RelatedResourceDTO> result = new ArrayList<>();
        Set<Integer> restMeasIds = new HashSet(); // 其他表达式指标集合
        Set<Integer> currentMeasIds = new HashSet();// 当前表达式指标集合
        Set<Integer> lackMeasIds = new HashSet();// 即将缺少的指标集合

        for (Integer dwTableId : dwTableIds) {
            DwTableCache tableCache = cacheManager.getDwTableCache(dwTableId);
            if (Objects.equals(dwTableId, application.getDwTableId())) {
                currentMeasIds.addAll(tableCache.getRelatedMeasureIds());
            } else {
                restMeasIds.addAll(tableCache.getRelatedMeasureIds());
            }
        }

        lackMeasIds.addAll(currentMeasIds);
        currentMeasIds.retainAll(restMeasIds);// 二者交集

        lackMeasIds.removeAll(currentMeasIds);// 去重之后就是真正失去血缘关系的指标集合


        if (CollectionUtils.isEmpty(lackMeasIds)) {
            return result;
        }
        Map<Long, Widget> widgetMap = cache.getRelatedWidgets().stream().collect(Collectors.toMap(Widget::getId, w -> w));
        Map<Long, DataSource> dataSourceMap = cache.getRelatedDataSources().stream().collect(Collectors.toMap(DataSource::getId, d -> d));
        Set<Long> widgetIds = new HashSet<>();
        Set<Long> dsIds = new HashSet<>();

        for (Integer measId : lackMeasIds) {
            Set<Long> widgets = cacheManager.getMeasureCache(measId).getRelatedWidgets().stream().filter(w -> widgetMap.keySet().contains(w.getId())).map(Widget::getId).collect(Collectors.toSet());
            widgetIds.addAll(widgets);

            Set<Long> datasources = cacheManager.getMeasureCache(measId).getRelatedDataSources().stream().filter(w -> dataSourceMap.keySet().contains(w.getId())).map(DataSource::getId).collect(Collectors.toSet());
            dsIds.addAll(datasources);

            IndicatorBean bean = new IndicatorBean();
            bean.setCode(cacheManager.getMeasureCache(measId).getCode());
            List<RelatedResourceDTO> dtos = measureMonitorReferenceService.listRelatedResource(bean);
            if (!CollectionUtils.isEmpty(dtos)) {
                result.addAll(dtos);
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
                dto.setName(widget.getName());
                dto.setTypeName("数据看板");
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
                dto.setSpaceId(dataSourceMap.get(id).getSpaceId());
                dto.setTypeName("数据集");
                dto.setType(0);
                return dto;
            }).collect(Collectors.toList());
            result.addAll(dtos);
        }

        return result;
    }

    public void saveApplication(DimensionApplicationVO applicationVO) {
        DimensionApplication dimensionApplication;
        if (Objects.nonNull(applicationVO.getDimAppId())) {
            // 更新
            dimensionApplication = dimensionApplicationService.getById(applicationVO.getDimAppId());
            IndicatorAssert.indicatorAssert(dimensionApplication == null, "表达式不存在");
            BeanUtils.copyProperties(applicationVO, dimensionApplication);
            dimensionApplication.setFactColumn(applicationVO.getColumnName());
        } else {
            dimensionApplication = new DimensionApplication();
            dimensionApplication.setAvailable(1);// TODO 一期全是可用
            dimensionApplication.setCreateTime(LocalDateTime.now());
            dimensionApplication.setDwTableId(applicationVO.getModelId());
            dimensionApplication.setDimId(applicationVO.getDimId());
            dimensionApplication.setSourceType(1); // TODO 一期只有doris
            dimensionApplication.setFactColumn(applicationVO.getColumnName());
            dimensionApplication.setDataType(applicationVO.getDataType());
        }
        dimensionApplicationService.saveOrUpdate(dimensionApplication, Wrappers.<DimensionApplication>lambdaQuery()
                .eq(DimensionApplication::getDwTableId, applicationVO.getModelId())
                .eq(DimensionApplication::getDimId, applicationVO.getDimId()));
        applicationVO.setDimAppId(dimensionApplication.getId());
    }

    public void deleteApplication(Integer appId) {
        dimensionApplicationService.removeById(appId);
    }

    public List<RelatedResourceDTO> disableApplication(Integer appId) {
        DimensionApplication app = dimensionApplicationService.getById(appId);
        IndicatorAssert.indicatorAssert(app == null, "表达式不存在,appId:" + appId);
        List<RelatedResourceDTO> relatedResourceDTOS = checkRelation(appId);
        if (CollectionUtils.isEmpty(relatedResourceDTOS)) {
            app.setAvailable(YesNoType.NO.getCode());
            dimensionApplicationService.updateById(app);
        }
        return relatedResourceDTOS;
    }

    public void enableApplication(Integer appId) {
        DimensionApplication app = dimensionApplicationService.getById(appId);
        IndicatorAssert.indicatorAssert(app == null, "表达式不存在,appId:" + appId);
        app.setAvailable(YesNoType.YES.getCode());
        dimensionApplicationService.updateById(app);
    }


    public List<DimensionVO> listDimension(List<Dimension> records) {
        List<Integer> queryMeasureIds = records.stream().map(Dimension::getId).collect(Collectors.toList());
        queryMeasureIds.add(-1);//集合为空时不报错
        List<DimensionVO> resultList = new ArrayList<>(records.size());
        Set<Integer> leafCategoryIds = records.stream().map(Dimension::getLeafCategoryId).collect(Collectors.toSet());
        Map<Integer, List<CategoryVO>> categoryInfoMap = categoryManager.findParentsByLeaf(leafCategoryIds);
        Set<Integer> createBys = records.stream().map(Dimension::getCreator).collect(Collectors.toSet());
        createBys.addAll(records.stream().map(Dimension::getUpdater).collect(Collectors.toSet()));
        Map<Integer, User> userMap = userManager.getUserMapByIds(createBys);
        records.forEach(dimension -> {
            DimensionVO dimensionVO = new DimensionVO();
            BeanUtils.copyProperties(dimension, dimensionVO);
            dimensionVO.setCreator(Optional.ofNullable(userMap)
                    .map(m -> m.get(dimension.getCreator()))
                    .orElse(null));
            dimensionVO.setUpdater(Optional.ofNullable(userMap)
                    .map(m -> m.get(dimension.getUpdater()))
                    .orElse(null));
            dimensionVO.setCategoryInfo(categoryInfoMap.get(dimension.getLeafCategoryId()));
            dimensionVO.setCreateTime(Timestamp.valueOf(dimension.getCreateTime()).getTime());
            dimensionVO.setUpdateTime(Timestamp.valueOf(dimension.getUpdateTime()).getTime());
            resultList.add(dimensionVO);
        });
        return resultList;
    }

    public boolean isDateTypeDimensionByCache(Integer dimId) {
        DimensionCache dimensionCache = cacheManager.getDimensionCache(dimId);
        if (dimensionCache == null) {
            return false;
        }
        Integer viewType = dimensionCache.getDimension().getViewType();
        return ViewType.isDate(viewType);
    }

    public boolean lastExpression(Integer dimId) {
        List<DimensionApplication> list = dimensionApplicationService.list(Wrappers.<DimensionApplication>lambdaQuery().eq(DimensionApplication::getDimId, dimId));
        return list.size() == 1;
    }

    /**
     * 获取最小粒度的日期维度
     *
     * @param dateTypeDimId
     * @return
     */
    public List<Level> getLeves(Integer dateTypeDimId) {
        Level level = levelMapper.selectOne(Wrappers.<Level>lambdaQuery().eq(Level::getDimId, dateTypeDimId));
        if (level != null) {
            List<Level> levels = levelMapper.selectList(Wrappers.<Level>lambdaQuery().eq(Level::getHierarchyId, level.getHierarchyId()));
            return levels;
        }
        return null;
    }

    public Dimension getNaturalDimensionByViewType(ViewType viewType) {
        if (viewType == null) {
            return null;
        }
        switch (viewType) {
            case DAY:
                return getDimensionByEnName(IndicatorConstant.INDICATOR_NATUAL_DIM_DAY);
            case WEEK:
                return getDimensionByEnName(IndicatorConstant.INDICATOR_NATUAL_DIM_WEEK);
            case MONTH:
                return getDimensionByEnName(IndicatorConstant.INDICATOR_NATUAL_DIM_MONTH);
            case SEASON:
                return getDimensionByEnName(IndicatorConstant.INDICATOR_NATUAL_DIM_SEASON);
            case YEAR:
                return getDimensionByEnName(IndicatorConstant.INDICATOR_NATUAL_DIM_YEAR);
            default:
                return null;
        }
    }

    public List<Dimension> listNaturalDimension() {
        return dimensionService.list(Wrappers.<Dimension>lambdaQuery().in(Dimension::getEnName, IndicatorConstant.INDICATOR_NATURAL_DIMENSIONS));
    }

    public Dimension getDimensionByEnName(String enName) {
        return dimensionService.getOne(Wrappers.<Dimension>lambdaQuery().eq(Dimension::getEnName, enName));
    }


    public Integer getDimensionValueCount(String dimCode) {
        return dimensionQueryService.getDimCount(dimCode);
    }


    public void saveTimestampDimension() {

    }

    public void deleteDimensionApplication(Integer dimensionApplicationId) {
        dimensionApplicationMapper.deleteById(dimensionApplicationId);
    }


    @Transactional(rollbackFor = Exception.class)
    public void deleteById(Integer dimId) {
        Dimension dimension = Optional.ofNullable(dimensionService.getById(dimId))
                .orElseThrow(() -> IndicatorParamNotValidException.error("维度不存在"));
        preDelete(dimension);
        // 删除维度
        dimensionService.removeById(dimId);
        // 删除维度应用表
        dimensionApplicationMapper.delete(Wrappers.<DimensionApplication>lambdaQuery().eq(DimensionApplication::getDimId, dimId));
        // 删除维度值
        dimensionValuesService.remove(Wrappers.<DimensionValues>lambdaQuery().eq(DimensionValues::getCode, dimension.getCode()));
        // 删除级联维度
        levelMapper.delete(Wrappers.<Level>lambdaQuery().eq(Level::getDimId, dimId));
        // 删除维度维表关联表
        dimensionDimtableConnectService.remove(Wrappers.<DimensionDimtableConnect>lambdaQuery().eq(DimensionDimtableConnect::getDimId, dimId));
        // 删除自然日映射表
        measureNaturalDateMappingService.remove(Wrappers.<MeasureNaturalDateMapping>lambdaQuery().eq(MeasureNaturalDateMapping::getTargetDimId, dimId));


    }

    @Transactional(rollbackFor = Exception.class)
    public void removeApp(Integer appId) {
        DimensionApplication application = dimensionApplicationService.getById(appId);
        if (application == null) {
            throw IndicatorParamNotValidException.error("数据不存在");
        }
        dimensionApplicationService.removeById(appId);
        // 删除自然日映射表
        measureNaturalDateMappingService.remove(Wrappers.<MeasureNaturalDateMapping>lambdaQuery()
                .eq(MeasureNaturalDateMapping::getTargetDimId, application.getDimId())
                .eq(MeasureNaturalDateMapping::getDwTableId, application.getDwTableId())
        );

    }

    @Autowired
    IMeasureNaturalDateMappingService measureNaturalDateMappingService;

    @Autowired
    private DataSourceService dataSourceService;

    public void preDelete(Dimension dimension) {
        // TODO 维度是否被数据集引用
        Long count = dataSourceService.getCountByDimCodeAndMeasCode(Arrays.asList(dimension.getCode()));
        if (count > 0) {
            throw IndicatorParamNotValidException.error("当前维度已经用于分析卡，暂不支持删除，请先解除关系后再删除");
        }
        // 维度是否被依赖
        List<ComplexMeasureDependencyTree> dependencyTrees = complexMeasureDependencyTreeService.list(Wrappers.<ComplexMeasureDependencyTree>lambdaQuery()
                .eq(ComplexMeasureDependencyTree::getDependencyId, dimension.getId())
                .eq(ComplexMeasureDependencyTree::getDependencyType, TableColumnType.DIMENSION.getCode().intValue()));
        if (!CollectionUtils.isEmpty(dependencyTrees)) {
            Integer measureId = dependencyTrees.stream().map(ComplexMeasureDependencyTree::getComplexMeasId).findFirst().get();
            Measure measure = measureMapper.selectById(measureId);
            throw IndicatorParamNotValidException.error("当前维度是指标【" + measure.getCnName() + "】的依赖维度，删除后计算指标则不可用，请先解除依赖关系后，再来删除");
        }
    }

    @Transactional(rollbackFor = Exception.class)
    public void saveDimensionValues(DimensionValuesCreateVO dimensionValuesCreateVO) {
        String dimCode = dimensionValuesCreateVO.getDimCode();
        Dimension dimension = new Dimension();
        dimension.initUpdate();
        if (CollectionUtils.isEmpty(dimensionValuesCreateVO.getDimensionValueItemList())) {
            // 映射关系为空时，退化成退化维
            dimension.setDimType(DimType.DEGENERATE_DIM.getValue());
        } else {
            dimension.setDimType(DimType.STD_WITHOUT_TABLE.getValue());
        }
        // 更新维度类型
        dimensionService.update(dimension, Wrappers.<Dimension>lambdaUpdate().eq(Dimension::getCode, dimCode));
        // 保存维值
        // 删除旧的维值
        dimensionValuesService.remove(Wrappers.<DimensionValues>lambdaQuery().eq(DimensionValues::getCode, dimCode));
        List<DimensionValueItem> dimensionValueItemList = dimensionValuesCreateVO.getDimensionValueItemList();
        LocalDateTime localDateTime = LocalDateTime.now();
        LocalDate localDate = LocalDate.now();

        List<DimensionValues> dimensionValuesList = dimensionValueItemList.stream().map(item -> {
            DimensionValues dimensionValues = new DimensionValues();
            dimensionValues.setCode(dimCode);
            dimensionValues.setDate(localDate);
            dimensionValues.setTimestamp(localDateTime);
            dimensionValues.setVKey(item.getQueryField());
            dimensionValues.setVValue(item.getDisplayField());
            return dimensionValues;
        }).collect(Collectors.toList());
        dimensionValuesService.saveBatch(dimensionValuesList);
    }

    /**
     * 检查维度是否可用
     * 可用条件
     * 1.在线状态
     * 2.标准维已关联维表，退化维已关联事实表，
     * // TODO 暂时只校验标准维
     *
     * @param dimensionId
     * @return
     */
    public boolean avaliable(Integer dimensionId) {
        Dimension dimension = dimensionService.getById(dimensionId);
        if (Objects.isNull(dimension) || Objects.equals(YesNoType.YES.getCode(), dimension.getOnline()) || Objects.equals(YesNoType.NO.getCode(), dimension.getIsDelete())) {
            return false;
        }

        return true;
    }

    /**
     * 检查维度是否可用
     * 可用条件
     * 1.在线状态
     * 2.标准维已关联维表，退化维已关联事实表，
     * // TODO 暂时只校验标准维
     *
     * @param dimCode
     * @return
     */
    public boolean avaliable(String dimCode) {
        Dimension dimension = dimensionService.getOne(Wrappers.<Dimension>lambdaQuery().eq(Dimension::getCode, dimCode));
        if (Objects.isNull(dimension) || Objects.equals(YesNoType.NO.getCode(), dimension.getOnline()) || Objects.equals(YesNoType.YES.getCode(), dimension.getIsDelete())) {
            return false;
        }
        return true;
    }

    /**
     * 获取与某个维度有相同维表的维度(不包含目标维度)
     *
     * @param dimId
     * @return
     */
    public List<Dimension> listDimensionWithSameHierarchy(Integer dimId) {
        Level level = levelMapper.selectOne(Wrappers.<Level>lambdaQuery().eq(Level::getDimId, dimId));
        if (level != null) {
            Integer hierarchyId = level.getHierarchyId();
            List<Dimension> dimensionList = levelMapper.selectList(Wrappers.<Level>lambdaQuery()
                            .eq(Level::getHierarchyId, hierarchyId))
                    .stream()
                    .sorted(Comparator.comparing(Level::getSequence))
                    .map(l -> {
                        Dimension dimension = dimensionService.getById(l.getDimId());
                        return dimension;
                    })
                    .collect(Collectors.toList());
            return dimensionList;
        }
        return Collections.EMPTY_LIST;
    }


    /**
     * 获取与某个维度有相同维表的维度(包含目标维度)
     *
     * @param dimId
     * @return
     */
    public List<Dimension> listDimensionWithSameDimTable(Integer dimId) {
        DimensionDimtableConnect dimtableConnect = dimensionDimtableConnectService.getOne(Wrappers.<DimensionDimtableConnect>lambdaQuery().eq(DimensionDimtableConnect::getDimId, dimId));
        if (dimtableConnect == null) {
            return Collections.EMPTY_LIST;
        }
        String tableName = dimtableConnect.getDimTableName();
        String schemaName = dimtableConnect.getSchemaName();
        List<DimensionDimtableConnect> dimtableConnects = dimensionDimtableConnectService.list(Wrappers.<DimensionDimtableConnect>lambdaQuery()
                .eq(DimensionDimtableConnect::getDimTableName, tableName).eq(DimensionDimtableConnect::getSchemaName, schemaName));
        Set<Integer> dimIds = dimtableConnects.stream().map(DimensionDimtableConnect::getDimId).collect(Collectors.toSet());
        if (CollectionUtils.isEmpty(dimIds)) {
            return Collections.EMPTY_LIST;
        }

        return dimensionService.listByIds(dimIds);
    }

    @Autowired
    CacheManager cacheManager;

    @CheckCacheVersion
    public List<Measure> fetchRelatedMeas(Integer dimensionId) {
        DimensionCache dimensionCache = cacheManager.getDimensionCache(dimensionId);
        if (Objects.isNull(dimensionCache)) {
            return Collections.EMPTY_LIST;
        }
        Set<Integer> relatedMeasureIds = dimensionCache.getRelatedMeasureIds();
        if (CollectionUtils.isEmpty(relatedMeasureIds)) {
            return Collections.EMPTY_LIST;
        }
        return measureMapper.selectBatchIds(relatedMeasureIds);
    }

    // private List<Measure> listRelatedMeas(List<Integer> dimIds){
    //     List<DimensionApplication> dimensionApplications = dimensionApplicationMapper.selectList(Wrappers.<DimensionApplication>lambdaQuery().in(DimensionApplication::getDimId, dimIds));
    //     List<Integer> tableIds = dimensionApplications.stream().map(DimensionApplication::getDwTableId).collect(Collectors.toList());
    //     List<MeasureApplication> measureApplications = Collections.EMPTY_LIST;
    //     if(!CollectionUtils.isEmpty(tableIds)){
    //         measureApplications = measureApplicationMapper.selectList(Wrappers.<MeasureApplication>lambdaQuery().in(MeasureApplication::getDwTableId, tableIds));
    //     }
    //     Set<Integer> measIds = measureApplications.stream().map(MeasureApplication::getMeasId).collect(Collectors.toSet());
    //     if (CollectionUtils.isEmpty(measIds)){
    //         return Collections.EMPTY_LIST;
    //     }
    //     return measureMapper.selectBatchIds(measIds);
    // }

    /**
     * 根据维度ID查询具有级联关系的更细粒度的维度列表(包含本身)
     *
     * @param dimensionId
     * @return
     */
    public List<Dimension> listLeSeqDimensions(Integer dimensionId) {
        Dimension dimension = dimensionService.getById(dimensionId);
        Level level = levelMapper.selectOne(Wrappers.<Level>lambdaQuery().eq(Level::getDimId, dimensionId));
        List<Dimension> result = new ArrayList<>();
        if (level != null) {
            // 有维度级联，需要找到对应的主维度，可能是其他维度也可能是他本身
            List<Level> levels = levelMapper.selectList(Wrappers.<Level>lambdaQuery().eq(Level::getHierarchyId, level.getHierarchyId()).ge(Level::getSequence, level.getSequence()));
            result = dimensionService.listByIds(levels.stream().map(Level::getDimId).collect(Collectors.toList()));
        } else {
            // 级别ID维null，没有设置级联维度
            result.add(dimension);
        }
        return result;
    }

    /**
     * 根据维度ID查询具级联关系中最细粒度的维度(包含本身)
     * 如果没有级联关系，返回本身
     *
     * @param dimensionId
     * @return
     */
    public Dimension getLeastSeqDimensions(Integer dimensionId) {
        Dimension dimension = dimensionService.getById(dimensionId);
        Level level = levelMapper.selectOne(Wrappers.<Level>lambdaQuery().eq(Level::getDimId, dimensionId));
        if (level != null) {
            // 有维度级联，需要找到对应的主维度，可能是其他维度也可能是他本身
            List<Level> levels = levelMapper.selectList(Wrappers.<Level>lambdaQuery().eq(Level::getHierarchyId, level.getHierarchyId()).ge(Level::getSequence, level.getSequence()));
            Integer dimId = levels.stream().sorted(Comparator.comparing(Level::getSequence)).findFirst().map(Level::getDimId).orElse(dimensionId);
            return dimensionService.getById(dimId);
        } else {
            // 级别ID维null，没有设置级联维度
            return dimension;
        }
    }


    /**
     * 获取自然日期关联的维度
     *
     * @param measId
     * @return
     */
    public Dimension getNaturalDimension(Integer measId) {
        List<MeasureNaturalDateMapping> dateMappings = measureNaturalDateMappingService.list(Wrappers.<MeasureNaturalDateMapping>lambdaQuery().eq(MeasureNaturalDateMapping::getMeasId, measId));
        if (CollectionUtils.isEmpty(dateMappings)) {
            return null;
        }
        MeasureNaturalDateMapping mapping = dateMappings.get(0);
        Dimension dimension = dimensionService.getById(mapping.getTargetDimId());
        return dimension;
    }


    /**
     * 根据维度ID查询具有级联关系的更大粒度的维度列表(包含本身)
     *
     * @param dimensionId
     * @return
     */
    public List<Dimension> listGeSeqDimensions(Integer dimensionId) {
        Dimension dimension = dimensionService.getById(dimensionId);
        Level level = levelMapper.selectOne(Wrappers.<Level>lambdaQuery().eq(Level::getDimId, dimensionId));
        List<Dimension> result = new ArrayList<>();
        if (level != null) {
            // 有维度级联，需要找到对应的主维度，可能是其他维度也可能是他本身
            List<Level> levels = levelMapper.selectList(Wrappers.<Level>lambdaQuery().eq(Level::getHierarchyId, level.getHierarchyId()).ge(Level::getSequence, level.getSequence()));
            result = dimensionService.listByIds(levels.stream().map(Level::getDimId).collect(Collectors.toList()));
        } else {
            // 级别ID维null，没有设置级联维度
            result.add(dimension);
        }
        return result;
    }

    public List<DimensionApplicationVO> fetchRelatedModel(Integer dimensionId) {
        DimensionCache dimensionCache = cacheManager.getDimensionCache(dimensionId);
        if (Objects.isNull(dimensionCache)) {
            return Collections.EMPTY_LIST;
        }
        Set<Integer> relatedDwTableIds = dimensionCache.getRelatedDwTableIds();
        relatedDwTableIds = relatedDwTableIds.stream().filter(id -> Objects.nonNull(id)).collect(Collectors.toSet());
        Map<Integer, DwTable> dwTableMap = cacheManager.getMetadataCache().getDwTableMap();
        Map<Integer, DimensionApplication> dimensionApplicationMap = cacheManager.getMetadataCache().getDimensionApplicationMap();
        return relatedDwTableIds.stream().map(tableId -> {
            DwTable dwTable = dwTableMap.get(tableId);
            DimensionApplicationVO dimensionApplicationVO = new DimensionApplicationVO();
            dimensionApplicationVO.setSchemaName(dwTable.getSchemaName());
            dimensionApplicationVO.setCnName(dwTable.getCnName());
            dimensionApplicationVO.setEnName(dwTable.getEnName());
            dimensionApplicationVO.setModelId(dwTable.getId());
            dimensionApplicationVO.setOnline(dwTable.getOnline());
            dimensionApplicationVO.setTableName(dwTable.getTableName());
            String columnName = dimensionApplicationMap.values().stream().filter(da -> Objects.equals(da.getDimId(), dimensionId) && Objects.equals(da.getDwTableId(), tableId)).map(DimensionApplication::getFactColumn).findFirst().orElse("");
            Integer available = dimensionApplicationMap.values().stream().filter(da -> Objects.equals(da.getDimId(), dimensionId) && Objects.equals(da.getDwTableId(), tableId)).map(DimensionApplication::getAvailable).findFirst().orElse(0);
            Integer appId = dimensionApplicationMap.values().stream().filter(da -> Objects.equals(da.getDimId(), dimensionId) && Objects.equals(da.getDwTableId(), tableId)).map(DimensionApplication::getId).findFirst().orElse(null);
            dimensionApplicationVO.setDimAppId(appId);
            dimensionApplicationVO.setColumnName(columnName);
            dimensionApplicationVO.setAvailable(available);
            return dimensionApplicationVO;
        }).collect(Collectors.toList());
    }

    /**
     * 根据维度找相关模型，查找逻辑仅是包含这个维度字段的模型，不包含级联维度的相关模型
     *
     * @param
     * @return
     */
    // private List<DwTable> fetchRelatedModel(List<Integer> dimIds){
    //     List<DimensionApplication> dimensionApplications = dimensionApplicationMapper.selectList(Wrappers.<DimensionApplication>lambdaQuery().in(DimensionApplication::getDimId, dimIds));
    //     List<Integer> tableIds = dimensionApplications.stream().map(DimensionApplication::getDwTableId).collect(Collectors.toList());
    //     if(CollectionUtils.isEmpty(tableIds)){
    //         return Collections.EMPTY_LIST;
    //     }
    //     return dwTableMapper.selectBatchIds(tableIds);
    // }
    @Transactional(rollbackFor = Exception.class)
    public boolean create(DimensionCreateVO dimensionVO, User user) {
        Dimension dimension = new Dimension();
        String code = dimension.initCreateWithCodePrefix(IndicatorConstant.DIMSENSION_CODE_PREFIX);
        BeanUtils.copyProperties(dimensionVO, dimension);
        dimension.setCode(code);
        dimension.setDimType(DimType.DEGENERATE_DIM.getValue());// 默认退化维
        dimensionService.save(dimension);
        return true;
    }

    @Autowired
    private RedisCacheService redisCacheService;

    @Autowired
    private ILevelService levelService;

    @Transactional(rollbackFor = Exception.class)
    public boolean update(DimensionUpdateVO dimensionUpdateVO, User user) {

        Dimension dimension = dimensionService.getById(dimensionUpdateVO.getId());
        if (Objects.equals(dimension.getDimType(), DimType.STD_WITH_TABLE.getValue()) && Objects.equals(DimFrontType.DIM_WITHOUT_TABLE.getCode(), dimensionUpdateVO.getFrontDimType())) {
            // 当前是有维表，不能更改维度类型了
            throw IndicatorParamNotValidException.error("有维表不允许更改为无维表");
        }

        if (Objects.equals(DimFrontType.DIM_WITH_TABLE.getCode(), dimensionUpdateVO.getFrontDimType())) {
            if (!StringUtils.hasLength(dimensionUpdateVO.getDimTableName())
                    || !StringUtils.hasLength(dimensionUpdateVO.getSchemaName())
                    || !StringUtils.hasLength(dimensionUpdateVO.getDisplayField())
                    || !StringUtils.hasLength(dimensionUpdateVO.getQueryField())) {
                String key = "dimension_update_interface_" + dimensionUpdateVO.getId();
                redisCacheService.lpush("dimension_update_interface_key_list", key);
                redisCacheService.put(key, dimensionUpdateVO, 7, TimeUnit.DAYS);
                throw IndicatorParamNotValidException.error("有维表类型维度必须配置库表信息,请求现场的redisKey:" + key);
            }
        }

        removeCascadeLeveDimensionById(dimensionUpdateVO.getId());
        removeDimensionValuesByDimCode(dimension.getCode());
        BeanUtils.copyProperties(dimensionUpdateVO, dimension);
        dimension.initUpdate();
        configCascadeDimension(dimensionUpdateVO);
        if (StringUtils.hasLength(dimensionUpdateVO.getDimTableName())
                && StringUtils.hasLength(dimensionUpdateVO.getSchemaName())
                && StringUtils.hasLength(dimensionUpdateVO.getDisplayField())
                && StringUtils.hasLength(dimensionUpdateVO.getQueryField())) {
            removeDimTableConnectByDimId(dimensionUpdateVO.getId());
            linkDimTable(dimensionUpdateVO);
        }
        Integer dimFrontType = dimensionUpdateVO.getFrontDimType();
        if (DimFrontType.DIM_WITHOUT_TABLE.getCode().intValue() == dimFrontType.intValue()) {
            // 在维度更新里，如果是无维表类型，就一定是退化维
            dimension.setDimType(DimType.DEGENERATE_DIM.getValue());
        } else {
            dimension.setDimType(DimType.STD_WITH_TABLE.getValue());
        }
        dimensionService.updateById(dimension);
        return true;
    }

    private void linkDimTable(DimensionUpdateVO dimensionUpdateVO) {
        DimensionDimtableConnect dimtableConnect = new DimensionDimtableConnect();
        dimtableConnect.initCreate();
        BeanUtils.copyProperties(dimensionUpdateVO, dimtableConnect);
        dimtableConnect.setDimPrimaryKey(dimensionUpdateVO.getQueryField());
        dimtableConnect.setDimValueColumn(dimensionUpdateVO.getDisplayField());
        dimtableConnect.setCreateTime(LocalDateTime.now());
        dimtableConnect.setUpdateTime(LocalDateTime.now());
        dimtableConnect.setDimValueColumn(dimensionUpdateVO.getDisplayField());
        dimtableConnect.setDimId(dimensionUpdateVO.getId());
        dimtableConnect.setWhereCondition(dimensionUpdateVO.getWhereCondition());
        if (StringUtils.hasLength(dimensionUpdateVO.getWhereCondition())) {
            checkWhereCondition(dimensionUpdateVO);
        }
        dimensionDimtableConnectService.saveOrUpdate(dimtableConnect, Wrappers.<DimensionDimtableConnect>lambdaUpdate().eq(DimensionDimtableConnect::getDimId, dimensionUpdateVO.getId()));
    }

    @Resource
    DorisQueryManager dorisQueryManager;

    private void checkWhereCondition(DimensionUpdateVO vo) {
        try {
            if (SqlInjectionUtils.check(vo.getDisplayField())
                    || SqlInjectionUtils.check(vo.getQueryField())
                    || SqlInjectionUtils.check(vo.getSchemaName())
                    || SqlInjectionUtils.check(vo.getDimTableName())
            ) {
                throw new IllegalArgumentException("非法请求");
            }
            CheckWhereUtil.setWhereCheckFlag(0xffffffff);
            CheckWhereUtil.checkWhere("where " + vo.getWhereCondition());

            String sql = "select " + vo.getDisplayField() + " as c1 ," + vo.getQueryField() + " as c2 from " + vo.getSchemaName() + "." + vo.getDimTableName() + " where " + vo.getWhereCondition() + " limit 0 ;";
            dorisQueryManager.execTest(sql);
        } catch (Exception e) {
            throw IndicatorParamNotValidException.error("where 条件不合法");
        }
    }

    // 正则匹配出条件部分 or and like

    /**
     * 配置级联维度
     *
     * @param dimensionUpdateVO
     */
    private void configCascadeDimension(DimensionUpdateVO dimensionUpdateVO) {
        if (!CollectionUtils.isEmpty(dimensionUpdateVO.getLevels())) {
            List<LevelVO> levels = dimensionUpdateVO.getLevels();
            Set<Integer> dimIds = levels.stream().map(LevelVO::getDimId).collect(Collectors.toSet());
            List<Level> cascadeLevels = levelMapper.selectList(Wrappers.<Level>lambdaQuery().in(Level::getDimId, dimIds));
            Integer hierarchyId = null;
            if (CollectionUtils.isEmpty(cascadeLevels)) {
                // 层次不存在，需要新建层次
                Hierarchy hierarchy = new Hierarchy();
                String code = hierarchy.initCreateWithCodePrefix(IndicatorConstant.HIERARCHY_CODE_PREFIX);
                hierarchy.setCode(code);
                Integer firstDimId = levels.stream().findFirst().map(LevelVO::getDimId).get();
                Dimension firstDimension = dimensionService.getById(firstDimId);
                hierarchy.setName(firstDimension.getCnName());
                hierarchyMapper.insert(hierarchy);
                hierarchyId = hierarchy.getId();
            } else {
                // 层次存在，需要更新层次对应的维度
                Set<Integer> hierarchyIds = cascadeLevels.stream().map(Level::getHierarchyId).collect(Collectors.toSet());
                if (hierarchyIds.size() > 1) {
                    throw IndicatorParamNotValidException.error("所选维度不在同一个层次");
                }
                hierarchyId = hierarchyIds.stream().findFirst().get();
                // 先删除旧的层级关系
                levelService.remove(Wrappers.<Level>lambdaQuery().eq(Level::getHierarchyId, hierarchyId));
            }
            List<Level> levelList = new ArrayList<>();
            for (int i = 0; i < levels.size(); i++) {
                LevelVO levelVO = levels.get(i);
                Integer sequence = i;
                Integer dimId = levelVO.getDimId();
                Level level = new Level();
                level.initCreate();
                level.setCode(IndicatorConstant.LEVEL_CODE_PREFIX + UUID.randomUUID().toString().replace("-", ""));
                level.setHierarchyId(hierarchyId);
                level.setSequence(sequence);
                level.setDimId(dimId);
                levelList.add(level);
            }
            levelService.saveBatch(levelList);
        } else {
            // 没有传级联维度，需要进行删除操作
            Level one = levelMapper.selectOne(Wrappers.<Level>lambdaQuery().eq(Level::getDimId, dimensionUpdateVO.getId()));
            if (Objects.nonNull(one)) {
                levelService.remove(Wrappers.<Level>lambdaQuery().eq(Level::getHierarchyId, one.getHierarchyId()));
            }
        }
    }


    private void removeCascadeLeveDimensionById(Integer dimId) {
        levelMapper.delete(Wrappers.<Level>lambdaQuery().eq(Level::getDimId, dimId));
    }


    private void removeDimensionValuesByDimCode(String dimCode) {
        dimensionValuesService.remove(Wrappers.<DimensionValues>lambdaQuery().eq(DimensionValues::getCode, dimCode));
    }

    private void removeDimTableConnectByDimId(Integer dimId) {
        dimensionDimtableConnectService.remove(Wrappers.<DimensionDimtableConnect>lambdaQuery().eq(DimensionDimtableConnect::getDimId, dimId));
    }
}
