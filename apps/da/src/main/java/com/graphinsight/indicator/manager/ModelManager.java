package com.graphinsight.indicator.manager;

import com.graphinsight.indicator.util.SqlInjectionUtils;
import com.alibaba.fastjson.JSON;
import com.baomidou.dynamic.datasource.annotation.DS;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.graphinsight.indicator.annotation.CheckCacheVersion;
import com.graphinsight.indicator.auto.entity.Dimension;
import com.graphinsight.indicator.auto.entity.DimensionApplication;
import com.graphinsight.indicator.auto.entity.DimensionDimtableConnect;
import com.graphinsight.indicator.auto.entity.DwColumn;
import com.graphinsight.indicator.auto.entity.DwTable;
import com.graphinsight.indicator.auto.entity.Hierarchy;
import com.graphinsight.indicator.auto.entity.Level;
import com.graphinsight.indicator.auto.entity.Measure;
import com.graphinsight.indicator.auto.entity.MeasureApplication;
import com.graphinsight.indicator.auto.entity.User;
import com.graphinsight.indicator.auto.mapper.DimensionApplicationMapper;
import com.graphinsight.indicator.auto.mapper.DimensionDimtableConnectMapper;
import com.graphinsight.indicator.auto.mapper.DimensionMapper;
import com.graphinsight.indicator.auto.mapper.DwColumnMapper;
import com.graphinsight.indicator.auto.mapper.DwTableMapper;
import com.graphinsight.indicator.auto.mapper.MeasureApplicationMapper;
import com.graphinsight.indicator.auto.mapper.MeasureMapper;
import com.graphinsight.indicator.auto.service.IDimensionApplicationService;
import com.graphinsight.indicator.auto.service.IDimensionDimtableConnectService;
import com.graphinsight.indicator.auto.service.IDimensionService;
import com.graphinsight.indicator.auto.service.IDwColumnService;
import com.graphinsight.indicator.auto.service.IHierarchyService;
import com.graphinsight.indicator.auto.service.ILevelService;
import com.graphinsight.indicator.auto.service.IMeasureApplicationService;
import com.graphinsight.indicator.auto.service.IMeasureService;
import com.graphinsight.indicator.constant.IndicatorConstant;
import com.graphinsight.indicator.doris.entity.Columns;
import com.graphinsight.indicator.enums.ColumnViewType;
import com.graphinsight.indicator.enums.DimType;
import com.graphinsight.indicator.enums.EntryType;
import com.graphinsight.indicator.enums.TableColumnType;
import com.graphinsight.indicator.enums.ViewType;
import com.graphinsight.indicator.enums.YesNoType;
import com.graphinsight.indicator.exception.IndicatorParamNotValidException;
import com.graphinsight.indicator.model.OperationItem;
import com.graphinsight.indicator.model.OperationItemBuilder;
import com.graphinsight.indicator.model.cache.DimensionCache;
import com.graphinsight.indicator.model.cache.DwTableCache;
import com.graphinsight.indicator.model.cache.MeasureApplicationCache;
import com.graphinsight.indicator.model.cache.MeasureCache;
import com.graphinsight.indicator.model.cache.MetadataCache;
import com.graphinsight.indicator.model.dto.DimensionConfig;
import com.graphinsight.indicator.model.dto.DwColumnDTO;
import com.graphinsight.indicator.model.dto.RelatedResourceDTO;
import com.graphinsight.indicator.model.vo.CategoryVO;
import com.graphinsight.indicator.model.vo.ComplexMeasureBaseVO;
import com.graphinsight.indicator.model.vo.DimensionApplicationVO;
import com.graphinsight.indicator.model.vo.DimensionVO;
import com.graphinsight.indicator.model.vo.ModelColumnVO;
import com.graphinsight.indicator.model.vo.ModelCreateVO;
import com.graphinsight.indicator.model.vo.ModelDetailVO;
import com.graphinsight.indicator.model.vo.ModelFieldVO;
import com.graphinsight.indicator.model.vo.ModelUpdateVO;
import com.graphinsight.indicator.model.vo.ModelVO;
import com.graphinsight.indicator.model.vo.OfflineRequest;
import com.graphinsight.indicator.util.DateDimensionCreateUtil;
import com.graphinsight.indicator.util.IndicatorAssert;
import com.graphinsight.indicator.util.UserThreadLocalUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;

import javax.annotation.Resource;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * @Author: lixiaolong
 * @Description: 元数据管理服务
 * @Date: 2021/11/19
 */
@Slf4j
@Service
@DS("mysql")
public class ModelManager {

    @Autowired
    private MeasureMapper measureMapper;
    @Autowired
    private DimensionMapper dimensionMapper;
    @Autowired
    private DwTableMapper dwTableMapper;
    @Autowired
    private DwColumnMapper dwColumnMapper;
    @Autowired
    private IDwColumnService dwColumnService;
    @Autowired
    private IMeasureService measureService;
    @Autowired
    private IDimensionService dimensionService;
    @Autowired
    private IDimensionApplicationService dimensionApplicationService;
    @Autowired
    private IMeasureApplicationService measureApplicationService;
    @Autowired
    private MeasureApplicationMapper measureApplicationMapper;
    @Autowired
    private DimensionApplicationMapper dimensionApplicationMapper;
    @Autowired
    private UserManager userManager;
    @Autowired
    private CacheManager cacheManager;
    @Resource
    DorisQueryManager dorisQueryManager;
    @Resource
    DimensionManager dimensionManager;

    public void online(Integer id){
        DwTable dwTable = dwTableMapper.selectById(id);
        IndicatorAssert.indicatorAssert(dwTable == null, "模型不存在,ID:" + id);
        List<MeasureApplication> measureApplications = measureApplicationService.list(Wrappers.<MeasureApplication>lambdaQuery().eq(MeasureApplication::getDwTableId, id));
        List<DimensionApplication> dimensionApplications = dimensionApplicationService.list(Wrappers.<DimensionApplication>lambdaQuery().eq(DimensionApplication::getDwTableId, id));
        measureApplications.forEach(ma -> ma.setAvailable(YesNoType.YES.getCode()));
        dimensionApplications.forEach(da -> da.setAvailable(YesNoType.YES.getCode()));
        measureApplicationService.updateBatchById(measureApplications);
        dimensionApplicationService.updateBatchById(dimensionApplications);
        dwTable.setOnline(YesNoType.YES.getCode());
        dwTableMapper.updateById(dwTable);
    }


    public void offline(OfflineRequest request){
        Integer id = request.getId();
        DwTable dwTable = dwTableMapper.selectById(id);
        IndicatorAssert.indicatorAssert(dwTable == null, "模型不存在,ID:" + id);
        List<MeasureApplication> measureApplications = measureApplicationService.list(Wrappers.<MeasureApplication>lambdaQuery()
                .eq(MeasureApplication::getDwTableId, id)
                .eq(MeasureApplication::getAvailable, YesNoType.YES.getCode()));

        if (!CollectionUtils.isEmpty(measureApplications)){
            for (MeasureApplication application : measureApplications) {
                List<RelatedResourceDTO> relatedResourceDTOS = measureManager.disabledExp(application.getId());
                String name = relatedResourceDTOS.stream().map(RelatedResourceDTO::getName).collect(Collectors.joining(","));
                if (!CollectionUtils.isEmpty(relatedResourceDTOS)){
                    throw IndicatorParamNotValidException.error("模型下线会导致相关资源：" + name + "失效，请先解除相关资源引用");
                }
            }
        }

        List<DimensionApplication> dimensionApplications = dimensionApplicationService.list(Wrappers.<DimensionApplication>lambdaQuery()
                .eq(DimensionApplication::getDwTableId, id)
                .eq(DimensionApplication::getAvailable, YesNoType.YES.getCode()));

        if (!CollectionUtils.isEmpty(dimensionApplications)){
            for (DimensionApplication application : dimensionApplications) {
                List<RelatedResourceDTO> relatedResourceDTOS = dimensionManager.disableApplication(application.getId());
                String name = relatedResourceDTOS.stream().map(RelatedResourceDTO::getName).collect(Collectors.joining(","));
                if (!CollectionUtils.isEmpty(relatedResourceDTOS)){
                    throw IndicatorParamNotValidException.error("模型下线会导致相关资源：" + name + "失效，请先解除相关资源引用");
                }
            }
        }
        dwTable.setOnline(YesNoType.NO.getCode());
        dwTable.setOfflineOperator(UserThreadLocalUtil.getUserName());
        dwTable.setOfflineRemark(request.getReason());
        dwTableMapper.updateById(dwTable);
    }


    /**
     * 获取待同步的模型字段
     *
     * @param modelId
     */
    public List<ModelColumnVO> listColumns(Integer modelId) {
        DwTable dwTable = dwTableMapper.selectById(modelId);
        IndicatorAssert.indicatorAssert(dwTable == null, "模型不存在");
        // 获取字段列表
        List<Columns> columns = dorisQueryManager.listColumns(Arrays.asList(dwTable.getSchemaName()), Arrays.asList(dwTable.getTableName()));
        if (CollectionUtils.isEmpty(columns)) {
            return Collections.EMPTY_LIST;
        }

        // 获取维度列表
        Map<String, DimensionVO> dimensionMap = new HashMap<>();
        List<DimensionApplication> dimensionApplications = dimensionApplicationService.list(Wrappers.<DimensionApplication>lambdaQuery().eq(DimensionApplication::getDwTableId, modelId));
        if (!CollectionUtils.isEmpty(dimensionApplications)) {
            Set<Integer> dimIds = dimensionApplications.stream().map(DimensionApplication::getDimId).collect(Collectors.toSet());
            List<Dimension> dimensions = dimensionService.listByIds(dimIds);
            List<DimensionVO> vos = dimensionManager.listDimension(dimensions);
            dimensionMap = vos.stream().collect(Collectors.toMap(DimensionVO::getEnName, vo -> vo));
        }
        Map<String, DimensionVO> finalDimensionMap = dimensionMap;
        Set<String> columnNames = dimensionApplications.stream().map(DimensionApplication::getFactColumn).map(factColumn -> {
            factColumn = factColumn.toLowerCase();
            if (factColumn.startsWith("date_format")) {
                factColumn = factColumn.substring(factColumn.indexOf("`") + 1, factColumn.lastIndexOf("`"));
            }
            return factColumn;
        }).collect(Collectors.toSet());

        List<ModelColumnVO> vos = columns.stream().filter(c -> !columnNames.contains(c.getColumnName())).map(c -> convertVO(c, finalDimensionMap)).collect(Collectors.toList());
        // 按照列名筛选
        return vos;
    }

    private ModelColumnVO convertVO(Columns columns, Map<String, DimensionVO> dimensionMap) {
        ModelColumnVO vo = new ModelColumnVO();
        vo.setColumnName(columns.getColumnName());
        vo.setColumnComment(columns.getColumnComment());
        vo.setDataType(columns.getColumnType());
        DimensionVO dimension = dimensionMap.get(vo.getColumnName());
        if (dimension != null) {
            BeanUtils.copyProperties(dimension, vo);
            vo.setDimensionId(dimension.getId());
        }
        return vo;
    }

    /**
     * 事实表 关联/创建 维度
     *
     * @param columnVOS
     * @param modelId
     */
    @Transactional(rollbackFor = Exception.class)
    public void syncColumns(List<ModelColumnVO> columnVOS, Integer modelId) {
        DwTable dwTable = dwTableMapper.selectById(modelId);
        List<ModelColumnVO> createList = columnVOS.stream().filter(vo -> Objects.equals(vo.getEntryType(), EntryType.CREATE)).collect(Collectors.toList());
        List<ModelColumnVO> linkedList = columnVOS.stream().filter(vo -> Objects.equals(vo.getEntryType(), EntryType.LINK)).collect(Collectors.toList());
        syncWithCreate(createList, dwTable);
        syncWithLink(linkedList, dwTable);
    }

    private void syncWithLink(List<ModelColumnVO> linkedList, DwTable dwTable) {
        if (!CollectionUtils.isEmpty(linkedList)) {
            // 创建维度
            List<DimensionConfig> dimensionConfigs = new ArrayList<>();
            linkedList.forEach(c -> getDimension(c, dwTable, dimensionConfigs, false));
            // 关联普通维度
            List<DimensionConfig> normalDimensions = dimensionConfigs.stream().filter(config -> !config.getSwitchType()).collect(Collectors.toList());
            linkNormalDimension(normalDimensions, dwTable);
            // 关联时间戳类型的维度
            List<DimensionConfig> switchDimensions = dimensionConfigs.stream().filter(DimensionConfig::getSwitchType).collect(Collectors.toList());
            linkTimestampDimensions(switchDimensions);
        }
    }

    /**
     * 新增维度
     *
     * @param createList
     * @param dwTable
     */
    private void syncWithCreate(List<ModelColumnVO> createList, DwTable dwTable) {
        if (!CollectionUtils.isEmpty(createList)) {
            // 创建维度
            List<DimensionConfig> dimensionConfigs = new ArrayList<>();
            createList.forEach(c -> getDimension(c, dwTable, dimensionConfigs, true));
            List<Dimension> dimensions = dimensionConfigs.stream().map(DimensionConfig::getDimension).collect(Collectors.toList());
            // 重名校验
            checkNameRepeat(dimensions);

            // 关联普通维度
            List<DimensionConfig> normalDimensions = dimensionConfigs.stream().filter(config -> !config.getSwitchType()).collect(Collectors.toList());
            if (!CollectionUtils.isEmpty(normalDimensions)) {
                // 保存维度
                List<Dimension> normalCreateDimensions = normalDimensions.stream().map(DimensionConfig::getDimension).collect(Collectors.toList());
                dimensionService.saveBatch(normalCreateDimensions);
                linkNormalDimension(normalDimensions, dwTable);
            }

            // 关联时间戳类型的维度
            List<DimensionConfig> switchDimensions = dimensionConfigs.stream().filter(DimensionConfig::getSwitchType).collect(Collectors.toList());
            linkTimestampDimensions(switchDimensions);
        }
    }

    private void checkNameRepeat(List<Dimension> dimensions) {
        List<String> dimensionCnNames = dimensions.stream().map(Dimension::getCnName).collect(Collectors.toList());
        if (!CollectionUtils.isEmpty(dimensionCnNames)) {
            List<Dimension> dimensionList = dimensionMapper.selectList(Wrappers.<Dimension>lambdaQuery().in(Dimension::getCnName, dimensionCnNames));
            if (!CollectionUtils.isEmpty(dimensionList)) {
                Set<String> repeatNames = dimensionList.stream().map(Dimension::getCnName).collect(Collectors.toSet());
                throw IndicatorParamNotValidException.error("维度中文名: " + repeatNames + " 重复");
            }
        }

        List<String> dimensionEnNames = dimensions.stream().map(Dimension::getCnName).collect(Collectors.toList());
        if (!CollectionUtils.isEmpty(dimensionEnNames)) {
            List<Dimension> dimensionList = dimensionMapper.selectList(Wrappers.<Dimension>lambdaQuery().in(Dimension::getCnName, dimensionEnNames));
            if (!CollectionUtils.isEmpty(dimensionList)) {
                Set<String> repeatNames = dimensionList.stream().map(Dimension::getEnName).collect(Collectors.toSet());
                throw IndicatorParamNotValidException.error("维度英文名: " + repeatNames + " 重复");
            }
        }
    }

    private void getDimension(ModelColumnVO vo, DwTable dwTable, List<DimensionConfig> dimensionConfigs, boolean isCreateDimension) {
        Dimension dimension;
        if (isCreateDimension) {
            dimension = new Dimension();
            String code = dimension.initCreateWithCodePrefix(IndicatorConstant.DIMSENSION_CODE_PREFIX);
            dimension.setCode(code);
            dimension.setEnName(vo.getEnName());
            dimension.setCnName(vo.getCnName());
            dimension.setDescription(vo.getDescription());
            dimension.setDeveloper(Optional.ofNullable(vo.getDeveloper()).map(User::getUsername).orElse(null));
            dimension.setLeafCategoryId(Optional.ofNullable(vo.getCategory()).map(CategoryVO::getId).orElse(null));
            // 新维度默认是退化维
            dimension.setDimType(DimType.DEGENERATE_DIM.getValue());
        } else {
            dimension = dimensionService.getById(vo.getDimensionId());
            IndicatorAssert.indicatorAssert(dimension == null, "维度不存在,id:" + vo.getDimensionId());
            IndicatorAssert.indicatorAssert(Objects.equals(dimension.getIsHyper(), YesNoType.YES.getCode()), "该维度属于公共抽象维度，不能用来关联事实表");
        }
        boolean isTimestamp = isTimestampDimension(vo);
        DimensionConfig config = new DimensionConfig();
        config.setDimension(dimension);
        config.setDataType(vo.getDataType());
        config.setCreateDimension(isCreateDimension);
        config.setOriginFactColumnName(vo.getColumnName());
        config.setDwTable(dwTable);
        config.setViewType(vo.getViewType());
        config.setSwitchType(isTimestamp);
        dimensionConfigs.add(config);
    }


    public List<ModelColumnVO> listSyncColumn(Integer modelId) {

        return null;
    }


    @Transactional(rollbackFor = Exception.class)
    @CheckCacheVersion
    public void delete(Integer dwTableId) {
        DwTableCache dwTableCache = cacheManager.getDwTableCache(dwTableId);
        if (Objects.isNull(dwTableCache)) {
            throw IndicatorParamNotValidException.error("模型不存在");
        }
        MetadataCache metadataCache = cacheManager.getMetadataCache();
        Map<Integer, DimensionApplication> dimensionApplicationMap = metadataCache.getDimensionApplicationMap();
        Set<Integer> relatedMeasureIds = dwTableCache.getRelatedMeasureIds();
        Set<Integer> relatedDimensionIds = dwTableCache.getRelatedDimensionIds();
        // 删除指标应用信息
        if (!CollectionUtils.isEmpty(relatedMeasureIds)) {
            relatedMeasureIds.forEach(measId -> {
                MeasureCache measureCache = cacheManager.getMeasureCache(measId);
                Measure measure = measureCache.getMeasure();
                if (measure != null) {
                    if (measureManager.beCited(measure.getCode()) && measureManager.lastExpression(measId)) {
                        throw IndicatorParamNotValidException.error("指标:" + measure.getCnName() + "已被数据集引用,不允许删除模型");
                    }

                }
                List<MeasureApplicationCache> measureApplicationCacheList = measureCache.getMeasureApplicationCacheList();
                Integer measAppId = measureApplicationCacheList.stream()
                        .filter(m -> Objects.equals(m.getRelatedDwTableId(), dwTableId))
                        .map(MeasureApplicationCache::getMeasAppId)
                        .findFirst()
                        .orElse(null);
                measureManager.deleteExpression(measAppId);
            });
        }

        // 删除维度应用信息
        List<DimensionApplication> dimensionApplications = dimensionApplicationService.list(Wrappers.<DimensionApplication>lambdaQuery().eq(DimensionApplication::getDwTableId, dwTableId));
        if (!CollectionUtils.isEmpty(dimensionApplications)) {
            dimensionApplications.forEach(da -> {
                Integer dimId = da.getDimId();
                Dimension dimension = dimensionService.getById(dimId);
                if (dimension != null && measureManager.beCited(dimension.getCode()) && dimensionManager.lastExpression(dimId)) {
                    throw IndicatorParamNotValidException.error("维度:" + dimension.getCnName() + "已被数据集引用,不允许删除模型");
                }
            });
        }
        if (!CollectionUtils.isEmpty(relatedDimensionIds)) {
            relatedDimensionIds.forEach(dimId -> {
                DimensionCache dimensionCache = cacheManager.getDimensionCache(dimId);
                Dimension dimension = dimensionCache.getDimension();
                if (dimension != null) {
                    if (measureManager.beCited(dimension.getCode()) && dimensionManager.lastExpression(dimId)) {
                        throw IndicatorParamNotValidException.error("维度:" + dimension.getCnName() + "已被数据集引用,不允许删除模型");
                    }
                }
                Set<Integer> selfAppIds = dimensionCache.getSelfAppIds();
                if (!CollectionUtils.isEmpty(selfAppIds)) {
                    selfAppIds.forEach(id -> {
                        DimensionApplication dimensionApplication = dimensionApplicationMap.get(id);
                        if (Objects.equals(dimensionApplication.getDwTableId(), dwTableId)) {
                            dimensionApplicationMapper.deleteById(id);
                        }
                    });
                }
            });
        }
        dimensionApplicationService.remove(Wrappers.<DimensionApplication>lambdaQuery().eq(DimensionApplication::getDwTableId, dwTableId));
        // 删除模型
        dwTableMapper.deleteById(dwTableId);

    }


    public ModelVO getModelVOByTableId(Integer dwTableId) {
        DwTable dwTable = dwTableMapper.selectById(dwTableId);
        ModelVO modelVO = new ModelVO();
        return getModelVOByTable(dwTable);
    }

    public ModelVO getModelVOByTable(DwTable dwTable) {
        ModelVO modelVO = new ModelVO();
        BeanUtils.copyProperties(dwTable, modelVO);
        User createUser = userManager.getUserById(dwTable.getCreator());
        User updateUser = userManager.getUserById(dwTable.getUpdater());
        modelVO.setUpdater(createUser);
        modelVO.setCreator(updateUser);
        modelVO.setCreateTime(Timestamp.valueOf(dwTable.getCreateTime()).getTime());
        modelVO.setUpdateTime(Timestamp.valueOf(dwTable.getUpdateTime()).getTime());
        return modelVO;
    }

    @Transactional(rollbackFor = Exception.class)
    @CheckCacheVersion
    public void update(ModelUpdateVO modelUpdateVO) {
        // 获取模型
        DwTable dwTable = dwTableMapper.selectById(modelUpdateVO.getId());

        // 获取指标、维度更新信息
        List<ModelFieldVO> modelFieldList = modelUpdateVO.getModelFieldList();

        // 获取当前指标列表和维度列表
        // List<MeasureApplication> measureApplications = measureApplicationMapper.selectList(Wrappers.<MeasureApplication>lambdaQuery().eq(MeasureApplication::getDwTableId, modelUpdateVO.getId()));
        // List<DimensionApplication> dimensionApplications = dimensionApplicationMapper.selectList(Wrappers.<DimensionApplication>lambdaQuery().eq(DimensionApplication::getDwTableId, modelUpdateVO.getId()));
        // List<Integer> measIds = measureApplications.stream().map(MeasureApplication::getMeasId).collect(Collectors.toList());
        DwTableCache dwTableCache = cacheManager.getDwTableCache(modelUpdateVO.getId());
        // List<Integer> dimIds = dimensionApplications.stream().map(DimensionApplication::getDimId).collect(Collectors.toList());
        // Map<String, Measure> oldMeasureMap = Optional.ofNullable(CollectionUtils.isEmpty(measIds) ? null : measureService.listByIds(measIds))
        //         .map(list -> list.stream().collect(Collectors.toMap(Measure::getEnName, m -> m)))
        //         .orElse(Collections.EMPTY_MAP);
        // Map<String, Dimension> oldDimensionMap = Optional.ofNullable(CollectionUtils.isEmpty(dimIds) ? null : dimensionService.listByIds(dimIds))
        //         .map(list -> list.stream().collect(Collectors.toMap(Dimension::getEnName, m -> m)))
        //         .orElse(Collections.EMPTY_MAP);
        Set<Integer> oldMeasIds = dwTableCache.getRelatedMeasureIds();
        Set<Integer> oldDimIds = dwTableCache.getRelatedDimensionIds();
        MetadataCache metadataCache = cacheManager.getMetadataCache();
        Map<Integer, Measure> allMeasureMap = metadataCache.getAllMeasureMap();
        Map<Integer, Dimension> allDimensionMap = metadataCache.getAllDimensionMap();
        Set<String> oldMeasEnNames = allMeasureMap.values().stream().filter(m -> oldMeasIds.contains(m.getId())).map(Measure::getEnName).collect(Collectors.toSet());
        Set<String> oldDimEnNames = allDimensionMap.values().stream().filter(d -> oldDimIds.contains(d.getId())).map(Dimension::getEnName).collect(Collectors.toSet());

        // 找到维度变指标、指标变维度的字段
        Map<String, Measure> deleteMeasureMap = new HashMap<>();// 需要删除的指标列名
        List<Measure> saveMeasureList = new ArrayList<>(); // 新增的指标，需要关联事实表
        List<Measure> updateMeasureList = new ArrayList<>(); // 需要更新和插入的指标
        Map<String, Dimension> deleteDimensionMap = new HashMap<>(); // 需要删除的维度列名,需要取消事实表关联
        List<Dimension> updateDimensionList = new ArrayList<>(); // 需要更新和插入的维度
        List<Dimension> saveDimensionList = new ArrayList<>(); // 需要更新和插入的维度,需要关联事实表
        modelFieldList.forEach(m -> {
            String enName = m.getEnName();
            if (Objects.equals(m.getType(), TableColumnType.MEASURE.getCode())) {
                Measure measure = new Measure();
                BeanUtils.copyProperties(m, measure);
                // TODO 一期没有指标运算表达式。后期加指标运算的话，需要在这里更新
                measure.setEnName(m.getEnName());
                measure.setCnName(m.getCnName());
                measure.initUpdate();
                if (!oldMeasEnNames.contains(enName)) {
                    Dimension dimension = new Dimension();
                    BeanUtils.copyProperties(m, dimension);
                    deleteDimensionMap.put(enName, dimension);
                    measure.setId(null);
                    measure.setCode(IndicatorConstant.MEASURE_CODE_PREFIX + UUID.randomUUID().toString().replaceAll("-", ""));
                    saveMeasureList.add(measure);
                } else {
                    updateMeasureList.add(measure);
                }
            }

            if (Objects.equals(m.getType(), TableColumnType.DIMENSION.getCode())) {
                Dimension dimension = new Dimension();
                BeanUtils.copyProperties(m, dimension);
                dimension.setEnName(m.getEnName());
                dimension.setCnName(m.getCnName());
                dimension.initUpdate();
                if (!oldDimEnNames.contains(enName)) {
                    Measure measure = new Measure();
                    BeanUtils.copyProperties(m, measure);
                    deleteMeasureMap.put(enName, measure);
                    dimension.setId(null);
                    dimension.setCode(IndicatorConstant.DIMSENSION_CODE_PREFIX + UUID.randomUUID().toString().replaceAll("-", ""));
                    saveDimensionList.add(dimension);
                } else {
                    updateDimensionList.add(dimension);
                }
            }
        });
        //2.更新指标和维度表
        measureService.updateBatchById(updateMeasureList);
        measureService.saveBatch(saveMeasureList);
        dimensionService.updateBatchById(updateDimensionList);
        dimensionService.saveBatch(saveDimensionList);
        //3.关联新的指标和维度
        List<DwColumn> dwColumnList = dwColumnMapper.selectList(Wrappers.<DwColumn>lambdaQuery().eq(DwColumn::getDwTableId, modelUpdateVO.getId()));
        Map<String, List<DwColumnDTO>> columnsMap = dwColumnList.stream().map(d -> {
            DwColumnDTO columnDTO = new DwColumnDTO();
            columnDTO.setColumnEnName(d.getName());
            BeanUtils.copyProperties(d, columnDTO);
            return columnDTO;
        }).collect(Collectors.groupingBy(DwColumnDTO::getColumnEnName));
        linkMeasure(saveMeasureList, dwTable, columnsMap);
        linkNormalDimension(saveDimensionList, dwTable, columnsMap);
        // 4.删除指标和维度以及事实表关联关系
        List<Integer> deleteDimIds = deleteDimensionMap.values().stream().map(Dimension::getId).collect(Collectors.toList());
        List<Integer> deleteMeasIds = deleteMeasureMap.values().stream().map(Measure::getId).collect(Collectors.toList());
        if (!CollectionUtils.isEmpty(deleteDimIds)) {
            List<DimensionDimtableConnect> dimensionDimtableConnects = dimensionDimtableConnectMapper.selectList(Wrappers.<DimensionDimtableConnect>lambdaQuery().in(DimensionDimtableConnect::getDimId, deleteDimIds));
            if (!CollectionUtils.isEmpty(dimensionDimtableConnects)) {
                throw new RuntimeException("维度ID为：" + dimensionDimtableConnects.stream().map(DimensionDimtableConnect::getDimId).collect(Collectors.toSet()) + "的维度已关联维表，不允许修改为字段属性");
            }
            dimensionService.removeByIds(deleteDimIds);
            List<DimensionApplication> list = dimensionApplicationMapper.selectList(Wrappers.<DimensionApplication>lambdaQuery().in(DimensionApplication::getDimId, deleteDimIds));
            dimensionApplicationMapper.deleteBatchIds(list.stream().map(DimensionApplication::getId).collect(Collectors.toList()));

        }
        if (!CollectionUtils.isEmpty(deleteMeasIds)) {
            measureService.removeByIds(deleteMeasIds);
            List<MeasureApplication> list = measureApplicationMapper.selectList(Wrappers.<MeasureApplication>lambdaQuery().in(MeasureApplication::getMeasId, deleteMeasIds));
            measureApplicationMapper.deleteBatchIds(list.stream().map(MeasureApplication::getId).collect(Collectors.toList()));
        }
    }

    @Autowired
    private DimensionDimtableConnectMapper dimensionDimtableConnectMapper;


    @Autowired
    private CategoryManager categoryManager;
    @Autowired
    MeasureManager measureManager;

    @CheckCacheVersion
    public ModelDetailVO detail(Integer id) {
        DwTable dwTable = dwTableMapper.selectById(id);
        if (Objects.isNull(dwTable)) {
            return null;
        }
        ModelDetailVO modelDetailVO = new ModelDetailVO();
        BeanUtils.copyProperties(dwTable, modelDetailVO);
        DwTableCache dwTableCache = cacheManager.getDwTableCache(id);
        if (Objects.isNull(dwTableCache)) {
            return modelDetailVO;
        }
        Set<Integer> relatedMeasureIds = dwTableCache.getRelatedMeasureIds();

        // 获取指标列表、关联的事实表
        List<Measure> measureList = new ArrayList<>();
        List<MeasureApplication> measureApplications = new ArrayList<>();
        if (!CollectionUtils.isEmpty(relatedMeasureIds)) {
            measureList.addAll(measureMapper.selectBatchIds(relatedMeasureIds));
            measureApplications.addAll(measureApplicationMapper.selectList(Wrappers.<MeasureApplication>lambdaQuery().in(MeasureApplication::getMeasId, relatedMeasureIds)));
        }
        Map<Integer, List<MeasureApplication>> measIdMeaAppsMap = measureApplications.stream().collect(Collectors.groupingBy(MeasureApplication::getMeasId));
        Set<Integer> relatedDimensionIds = dwTableCache.getRelatedDimensionIds();
        // 获取维度列表、维度关联的事实表
        List<Dimension> dimensionList = new ArrayList<>();
        List<DimensionApplication> dimensionApplications = new ArrayList<>();
        if (!CollectionUtils.isEmpty(relatedDimensionIds)) {
            dimensionList.addAll(dimensionMapper.selectBatchIds(relatedDimensionIds));
            dimensionApplications.addAll(dimensionApplicationMapper.selectList(Wrappers.<DimensionApplication>lambdaQuery().in(DimensionApplication::getDimId, relatedDimensionIds)));
        }
        Map<Integer, List<DimensionApplication>> dimIdDimAppsMap = dimensionApplications.stream().collect(Collectors.groupingBy(DimensionApplication::getDimId));


        // 获取所有表
        List<DwTable> dwTables = dwTableMapper.selectList(null);
        Map<Integer, DwTable> dwTableMap = dwTables.stream().collect(Collectors.toMap(DwTable::getId, d -> d));

        // 获取分类信息
        List<ModelFieldVO> modelFieldList = new ArrayList<>();
        Set<Integer> measLeafCategoryIds = measureList.stream().map(Measure::getLeafCategoryId).collect(Collectors.toSet());
        Set<Integer> creatorIds = measureList.stream().map(Measure::getCreator).collect(Collectors.toSet());
        creatorIds.addAll(dimensionList.stream().map(Dimension::getCreator).collect(Collectors.toSet()));
        Map<Integer, User> userMap = userManager.getUserMapByIds(creatorIds);

        Map<Integer, List<CategoryVO>> measCategoryInfoMap = categoryManager.findParentsByLeaf(measLeafCategoryIds);
        measureList.forEach(measure -> {
            ModelFieldVO modelFieldVO = new ModelFieldVO();
            BeanUtils.copyProperties(measure, modelFieldVO);
            modelFieldVO.setType(TableColumnType.MEASURE.getCode());
            List<MeasureApplication> apps = measIdMeaAppsMap.get(measure.getId());
            List<String> tableNames = new ArrayList<>();
            Set<String> dataTypes = new HashSet<>();
            List<ComplexMeasureBaseVO> complexMeasureBaseVOS = measureManager.getExpressionUnderRelatedModel(measure.getId(), id);
            if (apps != null) {
                for (MeasureApplication ma : apps) {
                    if (dwTableMap.get(ma.getDwTableId()) != null && Objects.equals(ma.getDwTableId(), id)) {
                        tableNames.add(dwTableMap.get(ma.getDwTableId()).getTableName());
                        if (Objects.nonNull(ma.getDataType())) {
                            dataTypes.add(ma.getDataType());
                        }
                        modelFieldVO.setColumnName(ma.getFactColumn());
                        break;
                    }
                }
                MeasureApplication application = apps.stream().filter(ma -> Objects.equals(ma.getDwTableId(), id)).findAny().orElse(null);
                modelFieldVO.setDeletable(application != null);
            } else {
                modelFieldVO.setDeletable(false);
            }
            List<String> filterNames = tableNames.stream().filter(name -> Objects.equals(name, dwTable.getTableName())).collect(Collectors.toList());
            modelFieldVO.setMeasureExpressions(complexMeasureBaseVOS);
            modelFieldVO.setTableNames(filterNames);
            modelFieldVO.setDataType(dataTypes);
            modelFieldVO.setCreator(userMap.get(measure.getCreator()) == null ? null : userMap.get(measure.getCreator()).getNickname());
            modelFieldVO.setUpdator(userMap.get(measure.getUpdater()) == null ? null : userMap.get(measure.getUpdater()).getNickname());
            modelFieldVO.setCategoryInfo(measCategoryInfoMap.get(measure.getLeafCategoryId()) == null ? Collections.EMPTY_LIST : measCategoryInfoMap.get(measure.getLeafCategoryId()));
            modelFieldList.add(modelFieldVO);
        });

        Set<Integer> dimLeafCategoryIds = dimensionList.stream().map(Dimension::getLeafCategoryId).collect(Collectors.toSet());
        Map<Integer, List<CategoryVO>> dimCategoryInfoMap = categoryManager.findParentsByLeaf(dimLeafCategoryIds);
        dimensionList.forEach(dimension -> {
            ModelFieldVO modelFieldVO = new ModelFieldVO();
            BeanUtils.copyProperties(dimension, modelFieldVO);
            modelFieldVO.setType(TableColumnType.DIMENSION.getCode());
            List<DimensionApplication> apps = dimIdDimAppsMap.get(dimension.getId());
            List<String> tableNames = new ArrayList<>();
            Set<String> dataTypes = new HashSet<>();
            if (!CollectionUtils.isEmpty(apps)) {
                for (DimensionApplication ma : apps) {
                    if (dwTableMap.get(ma.getDwTableId()) != null && Objects.equals(ma.getDwTableId(), id)) {
                        tableNames.add(dwTableMap.get(ma.getDwTableId()).getTableName());
                        if (Objects.nonNull(ma.getDataType())) {
                            dataTypes.add(ma.getDataType());
                        }
                        modelFieldVO.setColumnName(ma.getFactColumn());
                        break;
                    }
                }
                DimensionApplication application = apps.stream().filter(da -> Objects.equals(da.getDwTableId(), id)).findAny().orElse(null);
                modelFieldVO.setDeletable(application != null);
            } else {
                modelFieldVO.setDeletable(false);
            }
            modelFieldVO.setCreator(userMap.get(dimension.getCreator()) == null ? null : userMap.get(dimension.getCreator()).getNickname());
            modelFieldVO.setUpdator(userMap.get(dimension.getUpdater()) == null ? null : userMap.get(dimension.getUpdater()).getNickname());
            List<String> filterNames = tableNames.stream().filter(name -> Objects.equals(name, dwTable.getTableName())).collect(Collectors.toList());
            modelFieldVO.setTableNames(filterNames);
            modelFieldVO.setDataType(dataTypes);
            if (apps != null) {
                List<DimensionApplicationVO> vos = apps.stream().map(da -> {
                    DimensionApplicationVO dimensionApplicationVO = new DimensionApplicationVO();
                    dimensionApplicationVO.setModelId(da.getDwTableId());
                    dimensionApplicationVO.setDimAppId(da.getId());
                    return dimensionApplicationVO;
                }).collect(Collectors.toList());
                modelFieldVO.setDimensionExpressions(vos);
            }
            modelFieldVO.setCategoryInfo(dimCategoryInfoMap.get(dimension.getLeafCategoryId()) == null ? Collections.EMPTY_LIST : dimCategoryInfoMap.get(dimension.getLeafCategoryId()));
            modelFieldList.add(modelFieldVO);
        });
        modelDetailVO.setModelFieldList(modelFieldList);
        modelDetailVO.setCreateTime(Timestamp.valueOf(dwTable.getCreateTime()).getTime());
        modelDetailVO.setUpdateTime(Timestamp.valueOf(dwTable.getUpdateTime()).getTime());
        modelDetailVO.setUpdater(userManager.getUserByName(dwTable.getUpdateUser()));
        modelDetailVO.setCreator(userManager.getUserByName(dwTable.getCreateUser()));
        modelDetailVO.setDeveloper(userManager.getUserByName(dwTable.getDeveloper()));
        return modelDetailVO;
    }

    /**
     * !!!!!!!!重要提醒!!!!!!!!!
     * Colums信息来自Doris，mybatisPlus提供的多数据源方式在spring事务方法内是不生效的,所以这里column只能从外面传进来
     * 具体原因未知，猜测是因为spring用动态代理实现事务时，跟mybatisplus多数据源实现原理冲突
     *
     * @param modelVO
     * @param columns
     */
    @Transactional(rollbackFor = Exception.class)
    public void syncColumns(ModelCreateVO modelVO, List<DwColumnDTO> columns) {
        DwTable dwTable = dwTableMapper.selectById(modelVO.getId());
        Map<String, DwColumnDTO> columnsMap = columns.stream().collect(Collectors.toMap(c -> c.getColumnEnName().toLowerCase(), c -> {
            c.setColumnEnName(c.getColumnEnName().toLowerCase());
            return c;
        }));
        Set<String> columnNames = columnsMap.keySet().stream().map(s -> s.toLowerCase()).collect(Collectors.toSet());

        // save column
        List<DwColumn> dwColumnList = new ArrayList<>(columnsMap.values().size());
        columnsMap.values().forEach(c -> {
            DwColumn dwColumn = new DwColumn();
            dwColumn.setDataType(c.getDataType());
            dwColumn.setDwTableId(modelVO.getId());
            dwColumn.setName(c.getColumnEnName());
            dwColumnList.add(dwColumn);
        });
        dwColumnService.saveBatch(dwColumnList);
        //获取已存在的指标和维度
        List<Measure> existedMeasures = Collections.emptyList();
        List<Dimension> existedDimensions = Collections.emptyList();
        if (!CollectionUtils.isEmpty(columnNames)) {
            existedMeasures = measureMapper.selectList(Wrappers.<Measure>lambdaQuery().in(Measure::getEnName, columnNames));
            existedDimensions = dimensionMapper.selectList(Wrappers.<Dimension>lambdaQuery().in(Dimension::getEnName, columnNames));
        }
        //根据字段名匹配指标、维度
        List<String> existedMeasureNames = existedMeasures.stream().map(Measure::getEnName).collect(Collectors.toList());
        List<String> existedDimensionNames = existedDimensions.stream().map(Dimension::getEnName).collect(Collectors.toList());

        // 过滤掉已存在的列名
        Set<String> newColumn = columnNames;
        newColumn.removeAll(existedMeasureNames);
        newColumn.removeAll(existedDimensionNames);

        // 新列名按照数据类型初步划分指标和维度
        List<Dimension> newDimensions = new ArrayList<>();
        List<DimensionConfig> timestampDimensionConfig = new ArrayList<>();
        List<Measure> newMeasures = new ArrayList<>();
        newColumn.forEach(n -> {
            DwColumnDTO col = columnsMap.get(n);
            boolean isDimension = isDimension(col);
            boolean isTimestamp = isTimestampDimension(col);
            if (isDimension) {
                Dimension dimension = new Dimension();
                String code = dimension.initCreateWithCodePrefix(IndicatorConstant.DIMSENSION_CODE_PREFIX);
                dimension.setCode(code);
                dimension.setEnName(col.getColumnEnName());
                dimension.setCnName(col.getColumnCnName());
                dimension.setDescription(col.getDescription());
                dimension.setLeafCategoryId(col.getLeafCategoryId());
                // 新维度默认是退化维
                dimension.setDimType(DimType.DEGENERATE_DIM.getValue());
                if (isTimestamp) {
                    DimensionConfig config = new DimensionConfig();
                    config.setDimension(dimension);
                    config.setDataType(col.getDataType());
                    config.setOriginFactColumnName(col.getColumnEnName());
                    config.setDwTable(dwTable);
                    config.setViewType(col.getViewType());
                    config.setCreateDimension(true);
                    timestampDimensionConfig.add(config);
                } else {
                    newDimensions.add(dimension);
                }
            } else {
                Measure measure = new Measure();
                String code = measure.initCreateWithCodePrefix(IndicatorConstant.MEASURE_CODE_PREFIX);
                measure.setCode(code);
                measure.setLeafCategoryId(col.getLeafCategoryId());
                measure.setOnline(1);
                measure.setDescription(col.getDescription());
                measure.setCnName(col.getColumnCnName());
                measure.setEnName(col.getColumnEnName());
                newMeasures.add(measure);
            }
        });
        // 校验指标名是否和已有指标重复
        List<String> measureCnNames = newMeasures.stream().map(Measure::getCnName).collect(Collectors.toList());
        List<String> dimensionCnNames = newDimensions.stream().map(Dimension::getCnName).collect(Collectors.toList());
        if (!CollectionUtils.isEmpty(measureCnNames)) {
            List<Measure> measures = measureMapper.selectList(Wrappers.<Measure>lambdaQuery().in(Measure::getCnName, measureCnNames));
            if (!CollectionUtils.isEmpty(measures)) {
                Set<String> repeatNames = measures.stream().map(Measure::getCnName).collect(Collectors.toSet());
                throw IndicatorParamNotValidException.error("指标中文名: " + repeatNames + " 重复");
            }
        }

        if (!CollectionUtils.isEmpty(dimensionCnNames)) {
            List<Dimension> dimensions = dimensionMapper.selectList(Wrappers.<Dimension>lambdaQuery().in(Dimension::getCnName, dimensionCnNames));
            if (!CollectionUtils.isEmpty(dimensions)) {
                Set<String> repeatNames = dimensions.stream().map(Dimension::getCnName).collect(Collectors.toSet());
                throw IndicatorParamNotValidException.error("维度中文名: " + repeatNames + " 重复");
            }
        }
        // 保存指标和维度
        measureService.saveBatch(newMeasures);
        dimensionService.saveBatch(newDimensions);
        Map<String, List<DwColumnDTO>> dwColumnDTOMap = columnsMap.values().stream()
                .collect(Collectors.groupingBy(DwColumnDTO::getColumnEnName));
        // 模型关联指标和维度
        List<Measure> linkMeasures = new ArrayList<>();
        linkMeasures.addAll(newMeasures);
        linkMeasures.addAll(existedMeasures);
        linkMeasure(linkMeasures, dwTable, dwColumnDTOMap);
        List<Dimension> linkDimensions = new ArrayList<>();
        linkDimensions.addAll(newDimensions);
        existedDimensions.forEach(ed -> {
            if (isTimestampDimension(columnsMap.get(ed.getEnName()))) {
                DwColumnDTO col = columnsMap.get(ed.getEnName());
                DimensionConfig config = new DimensionConfig();
                config.setDimension(ed);
                config.setDataType(col.getDataType());
                config.setOriginFactColumnName(col.getColumnEnName());
                config.setOriginFactColumnName(col.getColumnEnName());
                config.setViewType(col.getViewType());
                config.setDwTable(dwTable);
                config.setCreateDimension(false);
                timestampDimensionConfig.add(config);
            } else {
                linkDimensions.add(ed);
            }
        });

        linkNormalDimension(linkDimensions, dwTable, dwColumnDTOMap);
        // 关联时间戳类型的维度
        linkTimestampDimensions(timestampDimensionConfig);
    }

    private void linkTimestampDimensions(List<DimensionConfig> dimensionConfigs) {
        if (CollectionUtils.isEmpty(dimensionConfigs)) {
            return;
        }
        dimensionConfigs.forEach(d -> {
            saveTimestampDimensionV2(d);
        });
    }

    @Autowired
    private IHierarchyService hierarchyService;
    @Autowired
    private ILevelService levelService;

    /**
     * 时间类型切换用这个方法代替原有方法
     * TODO
     *
     * @param dimensionConfig
     */
    private void saveTimestampDimensionV2(DimensionConfig dimensionConfig) {
        DwTable dwTable = dimensionConfig.getDwTable();
        Integer dimensionId = dimensionConfig.getDimension().getId();
        List<Dimension> timestampDimensions = DateDimensionCreateUtil.listDimensions(ColumnViewType.findByInt(dimensionConfig.getViewType()).orElseThrow(() -> IndicatorParamNotValidException.error("类型不合法")), dimensionConfig);
        if (dimensionConfig.getCreateDimension()) {
            // 新增维度
            dimensionService.saveBatch(timestampDimensions);
            dimensionId = dimensionConfig.getMasterDimension().getId();
            Hierarchy hierarchy = new Hierarchy();
            String hCode = hierarchy.initCreateWithCodePrefix(IndicatorConstant.HIERARCHY_CODE_PREFIX);
            hierarchy.setCode(hCode);
            hierarchyService.save(hierarchy);

            List<Level> levelList = new ArrayList<>();
            Collections.reverse(timestampDimensions);
            for (int i = 0; i < timestampDimensions.size(); i++) {
                Dimension dimension = timestampDimensions.get(i);
                Integer sequence = i;
                Integer dimId = dimension.getId();
                Level level = new Level();
                level.initCreate();
                level.setCode(IndicatorConstant.LEVEL_CODE_PREFIX + UUID.randomUUID().toString().replace("-", ""));
                level.setHierarchyId(hierarchy.getId());
                level.setSequence(sequence);
                level.setDimId(dimId);
                levelList.add(level);
            }
            levelService.saveBatch(levelList);
            hierarchy.setName(dimensionConfig.getDimension().getCnName());
            // 关联维表
            timestampDimensions.forEach(dimension -> {
                DimensionDimtableConnect connect = DateDimensionCreateUtil.getDimensionDimtableConnect(ViewType.findByInt(dimension.getViewType()).orElseThrow(() -> IndicatorParamNotValidException.error("viewType不合法")), dimension.getId());
                dimensionDimtableConnectService.saveOrUpdate(connect, Wrappers.<DimensionDimtableConnect>lambdaUpdate()
                        .eq(DimensionDimtableConnect::getDimId, dimension.getId()));
            });
        }
        // 保存维度应用表
        Dimension dimension = dimensionConfig.getDimension();
        DimensionApplication dimensionApplication = new DimensionApplication();
        dimensionApplication.setAvailable(1);// TODO 一期全是可用
        dimensionApplication.setCreateTime(LocalDateTime.now());
        dimensionApplication.setDwTableId(dwTable.getId());
        dimensionApplication.setDimId(dimensionId);
        dimensionApplication.setSourceType(1); // TODO 一期只有doris
        dimensionApplication.setFactColumn("date_format(  concat ( `" + dimensionConfig.getOriginFactColumnName() + "` , '-01-01' ), " + dimensionConfig.getMasterDiemnsionParttern() + " ) ");
        dimensionApplication.setDataType(dimensionConfig.getDataType());
        dimensionApplicationService.save(dimensionApplication);
        dimensionApplicationService.saveOrUpdate(dimensionApplication, Wrappers.<DimensionApplication>lambdaQuery()
                .eq(DimensionApplication::getDimId, dimension.getId())
                .eq(DimensionApplication::getDwTableId, dwTable.getId()));
    }

    private void saveTimestampDimension(DimensionConfig dimensionConfig) {
        DwTable dwTable = dimensionConfig.getDwTable();
        Dimension dimensionTemplate = dimensionConfig.getDimension();
        if (dimensionConfig.getCreateDimension()) {
            // 新增维度
            Dimension dayDimension = new Dimension();
            String dayCode = dayDimension.initCreateWithCodePrefix(IndicatorConstant.DIMSENSION_CODE_PREFIX);
            dayDimension.setCode(dayCode);
            dayDimension.setViewType(ViewType.DAY.getValue());
            dayDimension.setEnName(dimensionTemplate.getEnName());
            dayDimension.setCnName(dimensionTemplate.getCnName() + "_DAY");
            dayDimension.setDimType(DimType.STD_WITH_TABLE.getValue());

            Dimension weekDimension = new Dimension();
            String weekCode = weekDimension.initCreateWithCodePrefix(IndicatorConstant.DIMSENSION_CODE_PREFIX);
            weekDimension.setCode(weekCode);
            weekDimension.setEnName(dimensionTemplate.getEnName() + "_week_system");
            weekDimension.setCnName(dimensionTemplate.getCnName() + "_WEEK");
            weekDimension.setViewType(ViewType.WEEK.getValue());
            weekDimension.setDimType(DimType.STD_WITH_TABLE.getValue());

            Dimension monthDimension = new Dimension();
            String monthCode = monthDimension.initCreateWithCodePrefix(IndicatorConstant.DIMSENSION_CODE_PREFIX);
            monthDimension.setCode(monthCode);
            monthDimension.setEnName(dimensionTemplate.getEnName() + "_month_system");
            monthDimension.setCnName(dimensionTemplate.getCnName() + "_MONTH");
            monthDimension.setViewType(ViewType.MONTH.getValue());
            monthDimension.setDimType(DimType.STD_WITH_TABLE.getValue());

            Dimension seasonDimension = new Dimension();
            String seasonCode = seasonDimension.initCreateWithCodePrefix(IndicatorConstant.DIMSENSION_CODE_PREFIX);
            seasonDimension.setCode(seasonCode);
            seasonDimension.setEnName(dimensionTemplate.getEnName() + "_season_system");
            seasonDimension.setCnName(dimensionTemplate.getCnName() + "_QUARTER");
            seasonDimension.setViewType(ViewType.SEASON.getValue());
            seasonDimension.setDimType(DimType.STD_WITH_TABLE.getValue());

            Dimension yearDimension = new Dimension();
            String yearCode = yearDimension.initCreateWithCodePrefix(IndicatorConstant.DIMSENSION_CODE_PREFIX);
            yearDimension.setCode(yearCode);
            yearDimension.setEnName(dimensionTemplate.getEnName() + "_year_system");
            yearDimension.setCnName(dimensionTemplate.getCnName() + "_YEAR");
            yearDimension.setViewType(ViewType.YEAR.getValue());
            yearDimension.setDimType(DimType.STD_WITH_TABLE.getValue());

            List<Dimension> timestampDimensions = new LinkedList<>();
            timestampDimensions.add(yearDimension);
            timestampDimensions.add(seasonDimension);
            timestampDimensions.add(monthDimension);
            timestampDimensions.add(weekDimension);
            timestampDimensions.add(dayDimension);

            dimensionService.saveBatch(timestampDimensions);
            dimensionTemplate.setId(dayDimension.getId());

            Hierarchy hierarchy = new Hierarchy();
            String hCode = hierarchy.initCreateWithCodePrefix(IndicatorConstant.HIERARCHY_CODE_PREFIX);
            hierarchy.setCode(hCode);
            hierarchyService.save(hierarchy);

            List<Level> levelList = new ArrayList<>();
            for (int i = 0; i < timestampDimensions.size(); i++) {
                Dimension dimension = timestampDimensions.get(i);
                Integer sequence = i;
                Integer dimId = dimension.getId();
                Level level = new Level();
                level.initCreate();
                level.setCode(IndicatorConstant.LEVEL_CODE_PREFIX + UUID.randomUUID().toString().replace("-", ""));
                level.setHierarchyId(hierarchy.getId());
                level.setSequence(sequence);
                level.setDimId(dimId);
                levelList.add(level);

            }
            levelService.saveBatch(levelList);
            hierarchy.setName(dayDimension.getCnName());
            // 关联维表
            DimensionDimtableConnect dimtableConnectDay = new DimensionDimtableConnect();
            dimtableConnectDay.initCreate();
            dimtableConnectDay.setDimTableName("dim_day");
            dimtableConnectDay.setSchemaName("eps_service");
            dimtableConnectDay.setDimPrimaryKey("day_short_desc");
            dimtableConnectDay.setDimValueColumn("day_long_desc");
            dimtableConnectDay.setCreateTime(LocalDateTime.now());
            dimtableConnectDay.setUpdateTime(LocalDateTime.now());
            dimtableConnectDay.setDimId(dayDimension.getId());
            dimensionDimtableConnectService.saveOrUpdate(dimtableConnectDay, Wrappers.<DimensionDimtableConnect>lambdaUpdate()
                    .eq(DimensionDimtableConnect::getDimId, dayDimension.getId()));

            DimensionDimtableConnect dimtableConnectWeek = new DimensionDimtableConnect();
            dimtableConnectWeek.initCreate();
            dimtableConnectWeek.setDimTableName("dim_day");
            dimtableConnectWeek.setSchemaName("eps_service");
            dimtableConnectWeek.setDimPrimaryKey("week_short_desc");
            dimtableConnectWeek.setDimValueColumn("week_short_desc");
            dimtableConnectWeek.setCreateTime(LocalDateTime.now());
            dimtableConnectWeek.setUpdateTime(LocalDateTime.now());
            dimtableConnectWeek.setDimId(weekDimension.getId());
            dimensionDimtableConnectService.saveOrUpdate(dimtableConnectWeek, Wrappers.<DimensionDimtableConnect>lambdaUpdate()
                    .eq(DimensionDimtableConnect::getDimId, weekDimension.getId()));

            DimensionDimtableConnect dimtableConnectMonth = new DimensionDimtableConnect();
            dimtableConnectMonth.initCreate();
            dimtableConnectMonth.setDimTableName("dim_day");
            dimtableConnectMonth.setSchemaName("eps_service");
            dimtableConnectMonth.setDimPrimaryKey("month_short_desc");
            dimtableConnectMonth.setDimValueColumn("month_short_desc");
            dimtableConnectMonth.setCreateTime(LocalDateTime.now());
            dimtableConnectMonth.setUpdateTime(LocalDateTime.now());
            dimtableConnectMonth.setDimId(monthDimension.getId());
            dimensionDimtableConnectService.saveOrUpdate(dimtableConnectMonth, Wrappers.<DimensionDimtableConnect>lambdaUpdate()
                    .eq(DimensionDimtableConnect::getDimId, monthDimension.getId()));

            DimensionDimtableConnect dimtableConnectSeason = new DimensionDimtableConnect();
            dimtableConnectSeason.initCreate();
            dimtableConnectSeason.setDimTableName("dim_day");
            dimtableConnectSeason.setSchemaName("eps_service");
            dimtableConnectSeason.setDimPrimaryKey("quarter_short_desc");
            dimtableConnectSeason.setDimValueColumn("quarter_short_desc");
            dimtableConnectSeason.setCreateTime(LocalDateTime.now());
            dimtableConnectSeason.setUpdateTime(LocalDateTime.now());
            dimtableConnectSeason.setDimId(seasonDimension.getId());
            dimensionDimtableConnectService.saveOrUpdate(dimtableConnectSeason, Wrappers.<DimensionDimtableConnect>lambdaUpdate()
                    .eq(DimensionDimtableConnect::getDimId, seasonDimension.getId()));

            DimensionDimtableConnect dimtableConnectYear = new DimensionDimtableConnect();
            dimtableConnectYear.initCreate();
            dimtableConnectYear.setDimTableName("dim_day");
            dimtableConnectYear.setSchemaName("eps_service");
            dimtableConnectYear.setDimPrimaryKey("year_id");
            dimtableConnectYear.setDimValueColumn("year_id");
            dimtableConnectYear.setCreateTime(LocalDateTime.now());
            dimtableConnectYear.setUpdateTime(LocalDateTime.now());
            dimtableConnectYear.setDimId(yearDimension.getId());
            dimensionDimtableConnectService.saveOrUpdate(dimtableConnectYear, Wrappers.<DimensionDimtableConnect>lambdaUpdate()
                    .eq(DimensionDimtableConnect::getDimId, yearDimension.getId()));


        }
        // 保存维度应用表
        DimensionApplication dimensionApplication = new DimensionApplication();
        dimensionApplication.setAvailable(1);// TODO 一期全是可用
        dimensionApplication.setCreateTime(LocalDateTime.now());
        dimensionApplication.setDwTableId(dwTable.getId());
        dimensionApplication.setDimId(dimensionTemplate.getId());
        dimensionApplication.setSourceType(1); // TODO 一期只有doris
        dimensionApplication.setFactColumn("date_format( `" + dimensionTemplate.getEnName() + "` , '%Y-%m-%d' ) ");
        dimensionApplication.setDataType("datetime");
        dimensionApplicationService.save(dimensionApplication);
        dimensionApplicationService.saveOrUpdate(dimensionApplication, Wrappers.<DimensionApplication>lambdaQuery()
                .eq(DimensionApplication::getDimId, dimensionTemplate.getId())
                .eq(DimensionApplication::getDwTableId, dwTable.getId()));
    }

    @Autowired
    private IDimensionDimtableConnectService dimensionDimtableConnectService;


    private boolean isDimension(DwColumnDTO dwColumnDTO) {
        return Objects.equals(dwColumnDTO.getType(), TableColumnType.DIMENSION.getCode().intValue());
    }

    private boolean isTimestampDimension(DwColumnDTO dwColumnDTO) {
        if (Objects.isNull(dwColumnDTO.getViewType())) {
            return false;
        }
        return ViewType.switchable(dwColumnDTO.getViewType());
    }

    private boolean isTimestampDimension(ModelColumnVO dwColumnDTO) {
        if (Objects.isNull(dwColumnDTO.getViewType())) {
            return false;
        }
        return ViewType.switchable(dwColumnDTO.getViewType());
    }

    private void linkMeasure(List<Measure> measureList, DwTable dwTable, Map<String, List<DwColumnDTO>> columnsMap) {
        List<MeasureApplication> measureApplications = new ArrayList<>();
        for (Measure m : measureList) {
            if (SqlInjectionUtils.check(m.getEnName())) {
                throw new IllegalArgumentException(m.getEnName() + "非法字段请求");
            }
            MeasureApplication measureApplication = new MeasureApplication();
            // TODO 操作类型一期写死
            List<OperationItem> operationItems = Arrays.asList(OperationItemBuilder.builder());
            measureApplication.setExpression(JSON.toJSONString(operationItems));
            measureApplication.setApplyType(0);// TODO 一期只有原生指标
            measureApplication.setAvailable(1);// TODO 一期全是可用
            measureApplication.setCreateTime(LocalDateTime.now());
            measureApplication.setDwTableId(dwTable.getId());
            measureApplication.setFactColumn(m.getEnName());
            measureApplication.setMeasId(m.getId());
            measureApplication.setWhereCondition(null);// TODO 一起没有where条件
            List<DwColumnDTO> dwColumnDTOList = columnsMap.get(m.getEnName());
            if (!CollectionUtils.isEmpty(dwColumnDTOList)) {
                String dataType = dwColumnDTOList.stream()
                        .filter(dwColumnDTO -> Objects.equals(dwColumnDTO.getDwTableId(), measureApplication.getDwTableId()))
                        .map(DwColumnDTO::getDataType)
                        .findFirst()
                        .orElse(null);
                measureApplication.setDataType(dataType);
            }
            measureApplicationService.saveOrUpdate(measureApplication, Wrappers.<MeasureApplication>lambdaQuery()
                    .eq(MeasureApplication::getDwTableId, dwTable.getId())
                    .eq(MeasureApplication::getMeasId, m.getId()));
            measureApplications.add(measureApplication);
        }
        /**
         * 测试指标是否能通过sql检查
         */
        if (!CollectionUtils.isEmpty(measureApplications)) {
            dorisQueryManager.runTest(dwTable, measureApplications);
        }
    }


    private void linkNormalDimension(List<Dimension> dimensions, DwTable dwTable, Map<String, List<DwColumnDTO>> columnsMap) {
        dimensions.forEach(d -> {
            DimensionApplication dimensionApplication = new DimensionApplication();
            dimensionApplication.setAvailable(1);// TODO 一期全是可用
            dimensionApplication.setCreateTime(LocalDateTime.now());
            dimensionApplication.setDwTableId(dwTable.getId());
            dimensionApplication.setDimId(d.getId());
            dimensionApplication.setSourceType(1); // TODO 一期只有doris
            dimensionApplication.setFactColumn(d.getEnName());
            List<DwColumnDTO> dwColumnDTOList = columnsMap.get(d.getEnName());
            if (!CollectionUtils.isEmpty(dwColumnDTOList)) {
                String dataType = dwColumnDTOList.stream()
                        .filter(dwColumnDTO -> Objects.equals(dwColumnDTO.getDwTableId(), dimensionApplication.getDwTableId()))
                        .map(DwColumnDTO::getDataType)
                        .findFirst()
                        .orElse(null);
                dimensionApplication.setDataType(dataType);
            }
            dimensionApplicationService.saveOrUpdate(dimensionApplication, Wrappers.<DimensionApplication>lambdaQuery()
                    .eq(DimensionApplication::getDimId, d.getId())
                    .eq(DimensionApplication::getDwTableId, dwTable.getId()));
        });
    }

    private void linkNormalDimension(List<DimensionConfig> dimensionConfigs, DwTable dwTable) {
        dimensionConfigs.forEach(config -> {
            Dimension d = config.getDimension();
            DimensionApplication dimensionApplication = new DimensionApplication();
            dimensionApplication.setAvailable(1);// TODO 一期全是可用
            dimensionApplication.setCreateTime(LocalDateTime.now());
            dimensionApplication.setDwTableId(dwTable.getId());
            dimensionApplication.setDimId(d.getId());
            dimensionApplication.setSourceType(1); // TODO 一期只有doris
            dimensionApplication.setFactColumn(config.getOriginFactColumnName());
            dimensionApplication.setDataType(config.getDataType());
            dimensionApplicationService.saveOrUpdate(dimensionApplication, Wrappers.<DimensionApplication>lambdaQuery()
                    .eq(DimensionApplication::getDimId, d.getId())
                    .eq(DimensionApplication::getDwTableId, dwTable.getId()));
        });
    }
}
