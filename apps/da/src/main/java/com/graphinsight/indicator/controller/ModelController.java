package com.graphinsight.indicator.controller;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.graphinsight.indicator.annotation.CheckCacheVersion;
import com.graphinsight.indicator.annotation.CurrentUser;
import com.graphinsight.indicator.annotation.OperateLog;
import com.graphinsight.indicator.annotation.ReloadCache;
import com.graphinsight.indicator.annotation.SyncReloadCache;
import com.graphinsight.indicator.auto.entity.Category;
import com.graphinsight.indicator.auto.entity.Dimension;
import com.graphinsight.indicator.auto.entity.DimensionApplication;
import com.graphinsight.indicator.auto.entity.DwTable;
import com.graphinsight.indicator.auto.entity.Measure;
import com.graphinsight.indicator.auto.entity.MeasureApplication;
import com.graphinsight.indicator.auto.entity.User;
import com.graphinsight.indicator.auto.mapper.CategoryMapper;
import com.graphinsight.indicator.auto.mapper.DimensionApplicationMapper;
import com.graphinsight.indicator.auto.mapper.DimensionMapper;
import com.graphinsight.indicator.auto.mapper.DwTableMapper;
import com.graphinsight.indicator.auto.mapper.MeasureApplicationMapper;
import com.graphinsight.indicator.auto.mapper.MeasureMapper;
import com.graphinsight.indicator.auto.service.IDimensionApplicationService;
import com.graphinsight.indicator.auto.service.IDwTableService;
import com.graphinsight.indicator.auto.service.IMeasureApplicationService;
import com.graphinsight.indicator.constant.IndicatorConstant;
import com.graphinsight.indicator.doris.entity.Columns;
import com.graphinsight.indicator.doris.mapper.ColumnsMapper;
import com.graphinsight.indicator.enums.CheckNameType;
import com.graphinsight.indicator.enums.TableColumnType;
import com.graphinsight.indicator.exception.IndicatorParamNotValidException;
import com.graphinsight.indicator.manager.CacheManager;
import com.graphinsight.indicator.manager.CategoryManager;
import com.graphinsight.indicator.manager.DimensionManager;
import com.graphinsight.indicator.manager.ModelManager;
import com.graphinsight.indicator.manager.UserManager;
import com.graphinsight.indicator.model.Response;
import com.graphinsight.indicator.model.cache.MetadataCache;
import com.graphinsight.indicator.model.dto.DwColumnDTO;
import com.graphinsight.indicator.model.vo.CategoryVO;
import com.graphinsight.indicator.model.vo.CnNameRepeatCheckVO;
import com.graphinsight.indicator.model.vo.ModelBaseVO;
import com.graphinsight.indicator.model.vo.ModelColumnSyncParam;
import com.graphinsight.indicator.model.vo.ModelColumnVO;
import com.graphinsight.indicator.model.vo.ModelCreateVO;
import com.graphinsight.indicator.model.vo.ModelDetailVO;
import com.graphinsight.indicator.model.vo.ModelFieldRemove;
import com.graphinsight.indicator.model.vo.ModelFieldVO;
import com.graphinsight.indicator.model.vo.ModelPageQueryVO;
import com.graphinsight.indicator.model.vo.ModelQueryVO;
import com.graphinsight.indicator.model.vo.ModelUpdateVO;
import com.graphinsight.indicator.model.vo.ModelVO;
import com.graphinsight.indicator.model.vo.NameRepeatCheckVO;
import com.graphinsight.indicator.model.vo.OfflineRequest;
import com.graphinsight.indicator.model.vo.OriginMeasureCreateFieldVO;
import com.graphinsight.indicator.model.vo.PageVO;
import com.graphinsight.indicator.model.vo.TableCreateVO;
import com.graphinsight.indicator.model.vo.TableFieldVO;
import com.graphinsight.indicator.model.vo.TableUpdateVO;
import io.swagger.annotations.ApiOperation;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import springfox.documentation.annotations.ApiIgnore;

import javax.annotation.Resource;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * @Author: lixiaolong
 * @Description: 模型管理模块
 * @Date: 2021/11/19
 */
@RestController
@RequestMapping("/model")
public class ModelController {

    @Autowired
    private ModelManager modelManager;
    @Autowired
    private ColumnsMapper columnsMapper;
    @Autowired
    private DwTableMapper dwTableMapper;
    @Autowired
    private MeasureMapper measureMapper;
    @Autowired
    private DimensionMapper dimensionMapper;
    @Autowired
    private DimensionApplicationMapper dimensionApplicationMapper;
    @Autowired
    private MeasureApplicationMapper measureApplicationMapper;
    @Autowired
    private IDwTableService dwTableService;
    @Autowired
    CacheManager cacheManager;


    @PostMapping("/offline")
    @OperateLog
    @ApiOperation("模型下线接口")
    @SyncReloadCache
    public Response offline(@RequestBody OfflineRequest request) {
        modelManager.offline(request);
        return Response.ok();
    }

    @GetMapping("/online/{id}")
    @OperateLog
    @ApiOperation("模型上线接口")
    @SyncReloadCache
    public Response online(@PathVariable Integer id) {
        modelManager.online(id);
        return Response.ok();
    }


    @ApiOperation("获取待同步的字段列表(新)")
    @GetMapping("/sync/list/column/{modelId}")
    public Response<List<ModelColumnVO>> listSyncColumns(@PathVariable("modelId") Integer modelId) {
        List<ModelColumnVO> vos = modelManager.listColumns(modelId);
        return Response.ok(vos);
    }

    @ApiOperation("批量同步字段(新)")
    @SyncReloadCache
    @OperateLog
    @PostMapping("/sync/column/{modelId}")
    public Response<List<ModelColumnVO>> syncColumns(@PathVariable("modelId") Integer modelId, @RequestBody ModelColumnSyncParam param) {
        modelManager.syncColumns(param.getModelColumns(), modelId);
        return Response.ok();
    }

    @CheckCacheVersion
    @PostMapping("/cnName/repeat")
    @ApiOperation("判断中文名是否重复")
    public Response<Boolean> cnNameIsRepeat(@RequestBody @Validated CnNameRepeatCheckVO checkVO) {
        MetadataCache metadataCache = cacheManager.getMetadataCache();
        if (metadataCache == null) {
            throw IndicatorParamNotValidException.error("系统异常,请稍后再试");
        }
        if (CheckNameType.FIELD.getCode() == checkVO.getType().intValue()) {
            Set<String> dimensionCnNames = metadataCache.getAllDimensionMap().values().stream().map(Dimension::getCnName).collect(Collectors.toSet());
            Set<String> measureCnNames = metadataCache.getAllMeasureMap().values().stream().map(Measure::getCnName).collect(Collectors.toSet());
            if (dimensionCnNames.contains(checkVO.getCnName()) || measureCnNames.contains(checkVO.getCnName())) {
                return Response.ok(true);
            }
        } else if (CheckNameType.FIELD.getCode() == checkVO.getType().intValue()) {
            Set<String> dwTableCnNames = metadataCache.getDwTableMap().values().stream().map(DwTable::getCnName).collect(Collectors.toSet());
            if (dwTableCnNames.contains(checkVO.getCnName())) {
                return Response.ok(true);
            }
        } else {
            throw IndicatorParamNotValidException.error("类型不合法");
        }
        return Response.ok(false);
    }

    @CheckCacheVersion
    @PostMapping("/dimension/name/repeat")
    @ApiOperation("维度中英文名称是否重复")
    public Response<Boolean> enNameIsRepeat(@RequestBody @Validated NameRepeatCheckVO checkVO) {
        MetadataCache metadataCache = cacheManager.getMetadataCache();
        if (metadataCache == null) {
            throw IndicatorParamNotValidException.error("系统异常,请稍后再试");
        }
        Set<String> dimensionCnNames = metadataCache.getAllDimensionMap().values().stream().map(Dimension::getCnName).collect(Collectors.toSet());
        Set<String> dimensionEnNames = metadataCache.getAllDimensionMap().values().stream().map(Dimension::getEnName).collect(Collectors.toSet());
        if (dimensionCnNames.contains(checkVO.getName()) || dimensionEnNames.contains(checkVO.getName())) {
            return Response.ok(true);
        }
        return Response.ok(false);
    }


    @PostMapping("/update")
    @ReloadCache
    @OperateLog
    @ApiOperation("更新模型接口")
    public Response<ModelBaseVO> updateModel(@RequestBody @Validated TableUpdateVO modelVO) {
        DwTable dwTable = dwTableMapper.selectById(modelVO.getId());
        BeanUtils.copyProperties(modelVO, dwTable);
        dwTable.initUpdate();
        dwTableService.saveOrUpdate(dwTable);
        ModelBaseVO result = new ModelBaseVO();
        BeanUtils.copyProperties(dwTable, result);
        return Response.ok(result);
    }

    @PostMapping("/create")
    @ReloadCache
    @OperateLog
    @ApiOperation("创建模型接口")
    public Response<ModelBaseVO> saveModel(@RequestBody @Validated TableCreateVO modelVO) {
        String enName = modelVO.getEnName();
        String tableName = modelVO.getTableName();
        DwTable dwTable = dwTableMapper.selectOne(Wrappers.<DwTable>lambdaQuery().eq(DwTable::getTableName, tableName));
        if (dwTable != null) {
            return Response.error("表已存在");
        }
        dwTable = dwTableMapper.selectOne(Wrappers.<DwTable>lambdaQuery().eq(DwTable::getEnName, enName));
        if (dwTable != null) {
            return Response.error("英文名重复");
        }
        dwTable = new DwTable();
        dwTable.initCreate();
        BeanUtils.copyProperties(modelVO, dwTable);
        dwTableMapper.insert(dwTable);
        ModelBaseVO result = new ModelBaseVO();
        BeanUtils.copyProperties(dwTable, result);
        return Response.ok(result);
    }

    private boolean switched(Integer dimId, Map<Integer, List<DimensionApplication>> dimIdAppMap) {
        List<DimensionApplication> dimensionApplications = dimIdAppMap.get(dimId);
        if (!CollectionUtils.isEmpty(dimensionApplications)) {
            Set<DimensionApplication> formated = dimensionApplications.stream().filter(da -> da.getFactColumn().startsWith("date_format")).collect(Collectors.toSet());
            return formated.size() > 0;
        }
        return false;

    }

    private boolean dimConnected(Integer dimId, Integer modelId, Map<Integer, List<DimensionApplication>> dimIdAppMap) {
        List<DimensionApplication> dimensionApplications = dimIdAppMap.get(dimId);
        if (!CollectionUtils.isEmpty(dimensionApplications)) {
            Set<DimensionApplication> formated = dimensionApplications.stream().filter(da -> da.getDwTableId().intValue() == modelId.intValue()).collect(Collectors.toSet());
            return formated.size() > 0;
        }
        return false;

    }

    private boolean measConnected(Integer measId, Integer modelId, Map<Integer, List<MeasureApplication>> measIdAppMap) {
        List<MeasureApplication> measureApplications = measIdAppMap.get(measId);
        if (!CollectionUtils.isEmpty(measureApplications)) {
            Set<MeasureApplication> formated = measureApplications.stream().filter(da -> da.getDwTableId().intValue() == modelId.intValue()).collect(Collectors.toSet());
            return formated.size() > 0;
        }
        return false;

    }

    @GetMapping("/list/table/column/{id}")
    @ApiOperation("获取模型字段")
    public Response<List<TableFieldVO>> listColumn(@PathVariable Integer id) {
        DwTable dwTable = dwTableMapper.selectById(id);
        List<Columns> columns = columnsMapper.selectList(Wrappers.<Columns>lambdaQuery()
                .eq(Columns::getTableSchema, dwTable.getSchemaName())
                .eq(Columns::getTableName, dwTable.getTableName())
                .notIn(Columns::getDataType, IndicatorConstant.DATA_TYPE_BLACK_LIST));
        List<TableFieldVO> fieldVOS = columns.stream().map(col -> {
            TableFieldVO tableFieldVO = new TableFieldVO();
            tableFieldVO.setEnName(col.getColumnName());
            tableFieldVO.setCnName(col.getColumnComment());
            return tableFieldVO;
        }).collect(Collectors.toList());
        return Response.ok(fieldVOS);
    }

    @GetMapping("/list/column/{id}")
    @ApiOperation("获取待同步的模型字段")
    public Response<List<TableFieldVO>> preCreateModel(@PathVariable Integer id) {
        List<Dimension> dimensions = dimensionMapper.selectList(null);
        Set<String> dimCnNames = dimensions.stream().map(Dimension::getCnName).collect(Collectors.toSet());
        Set<String> dimEnNames = dimensions.stream().map(Dimension::getEnName).collect(Collectors.toSet());
        List<Measure> measures = measureMapper.selectList(null);
        Set<String> measCnNames = measures.stream().map(Measure::getCnName).collect(Collectors.toSet());
        Set<String> measEnNames = measures.stream().map(Measure::getEnName).collect(Collectors.toSet());
        DwTable dwTable = dwTableMapper.selectById(id);

        List<Columns> columns = columnsMapper.selectList(Wrappers.<Columns>lambdaQuery()
                .eq(Columns::getTableSchema, dwTable.getSchemaName())
                .eq(Columns::getTableName, dwTable.getTableName())
                .notIn(Columns::getDataType, IndicatorConstant.DATA_TYPE_BLACK_LIST));

        Map<String, Columns> columnsMap = columns.stream().collect(Collectors.toMap(Columns::getColumnName, c -> c));
        Set<String> columnNames = new HashSet<>();
        columnNames.addAll(columnsMap.keySet());
        List<TableFieldVO> tableFieldVOList = new ArrayList<>();
        if (!CollectionUtils.isEmpty(columnsMap)) {
            // 获取已经关联过的字段
            List<MeasureApplication> measureApplications = measureApplicationMapper.selectList(Wrappers.<MeasureApplication>lambdaQuery().eq(MeasureApplication::getDwTableId, id));
            List<DimensionApplication> dimensionApplications = dimensionApplicationMapper.selectList(Wrappers.<DimensionApplication>lambdaQuery().eq(DimensionApplication::getDwTableId, id));
            if (!CollectionUtils.isEmpty(measureApplications)) {
                Map<Integer, List<MeasureApplication>> measIdAppMap = measureApplications.stream().collect(Collectors.groupingBy(MeasureApplication::getMeasId));
                List<Measure> measureList = measureMapper.selectBatchIds(measIdAppMap.keySet());
                if (!CollectionUtils.isEmpty(measureList)) {
                    measureList.forEach(m -> {
                        // 排除手动创建的原子指标，因为这种类型的指标并不是表的字段
                        if (columnNames.contains(m.getEnName())) {
                            TableFieldVO tableFieldVO = new TableFieldVO();
                            BeanUtils.copyProperties(m, tableFieldVO);
                            tableFieldVO.setLeafCategoryId(m.getLeafCategoryId());
                            tableFieldVO.setType(TableColumnType.MEASURE.getCode());
                            tableFieldVO.setDataType(measIdAppMap.get(m.getId()).stream().map(MeasureApplication::getDataType).collect(Collectors.toSet()));
                            if (!measConnected(m.getId(), id, measIdAppMap)) {
                                tableFieldVOList.add(tableFieldVO);
                            }
                            columnNames.remove(m.getEnName());
                        }

                    });
                }
            }
            if (!CollectionUtils.isEmpty(dimensionApplications)) {
                Map<Integer, List<DimensionApplication>> dimIdAppMap = dimensionApplications.stream().collect(Collectors.groupingBy(DimensionApplication::getDimId));
                List<Dimension> dimensionList = dimensionMapper.selectBatchIds(dimIdAppMap.keySet());
                if (!CollectionUtils.isEmpty(dimensionList)) {
                    dimensionList.forEach(d -> {
                        if (columnNames.contains(d.getEnName())) {
                            TableFieldVO tableFieldVO = new TableFieldVO();
                            BeanUtils.copyProperties(d, tableFieldVO);
                            // 以_system结尾的维度，且已经同步过的，说明是类型转换过得维度，暂不支持切换类型转换
                            tableFieldVO.setSwitchType(switched(d.getId(), dimIdAppMap));
                            tableFieldVO.setType(TableColumnType.DIMENSION.getCode());
                            tableFieldVO.setLeafCategoryId(d.getLeafCategoryId());
                            tableFieldVO.setViewType(d.getViewType());
                            tableFieldVO.setDataType(dimIdAppMap.get(d.getId()).stream().map(DimensionApplication::getDataType).collect(Collectors.toSet()));
                            if (!dimConnected(d.getId(), id, dimIdAppMap)) {
                                tableFieldVOList.add(tableFieldVO);
                            }
                            columnNames.remove(d.getEnName());
                        }
                    });
                }
            }
        }
        if (!CollectionUtils.isEmpty(columnNames)) {
            columnNames.forEach(name -> {
                TableFieldVO tableFieldVO = new TableFieldVO();
                Columns c = columnsMap.get(name);
                tableFieldVO.setCnName(c.getColumnComment());
                tableFieldVO.setEnName(c.getColumnName());
                tableFieldVO.setSwitchType(true);
                tableFieldVO.setDataType(Arrays.asList(c.getDataType()).stream().collect(Collectors.toSet()));
                if (Objects.isNull(c.getDataType())) {
                    tableFieldVO.setType(TableColumnType.DIMENSION.getCode());
                } else {
                    tableFieldVO.setType(IndicatorConstant.MEASURE_DATA_TYPES.contains(c.getDataType().toLowerCase()) ? TableColumnType.MEASURE.getCode() : TableColumnType.DIMENSION.getCode());
                }
                String columnComment = c.getColumnComment();
                if (dimCnNames.contains(columnComment) || measCnNames.contains(columnComment)) {
                    tableFieldVO.setCnNameRepeat(true);
                } else {
                    tableFieldVO.setCnNameRepeat(false);
                }

                String columnName = c.getColumnName();
                if (dimEnNames.contains(columnName) || measEnNames.contains(columnName)) {
                    tableFieldVO.setEnNameRepeat(true);
                } else {
                    tableFieldVO.setEnNameRepeat(false);
                }
                tableFieldVOList.add(tableFieldVO);
            });
        }
        tableFieldVOList.forEach(tableFieldVO -> {
            tableFieldVO.setSync(measEnNames.contains(tableFieldVO.getEnName()) || dimEnNames.contains(tableFieldVO.getEnName()));
        });
        return Response.ok(tableFieldVOList);
    }


    @PostMapping("/sync/column")
    @SyncReloadCache
    @OperateLog
    @ApiOperation("同步模型字段接口")
    public Response createModel(@ApiIgnore @CurrentUser User user, @RequestBody @Validated ModelCreateVO modelVO) {
        List<OriginMeasureCreateFieldVO> filedVOList = modelVO.getColumns();

        DwTable dwTable = dwTableMapper.selectById(modelVO.getId());
        Map<String, OriginMeasureCreateFieldVO> fieldVOMap = filedVOList.stream().collect(Collectors.toMap(OriginMeasureCreateFieldVO::getEnName, o -> o));
        List<Columns> columnsList = columnsMapper.selectList(Wrappers.<Columns>lambdaQuery()
                .eq(Columns::getTableName, dwTable.getTableName())
                .eq(Columns::getTableSchema, dwTable.getSchemaName())
                .in(Columns::getColumnName, filedVOList.stream().map(OriginMeasureCreateFieldVO::getEnName).collect(Collectors.toSet())));
        Map<String, Columns> columnsMap = columnsList.stream().collect(Collectors.toMap(Columns::getColumnName, c -> c));
        List<DwColumnDTO> dwColumnDTOList = new ArrayList<>();
        filedVOList.forEach(f -> {
            DwColumnDTO col = new DwColumnDTO();
            BeanUtils.copyProperties(f, col);
            OriginMeasureCreateFieldVO originMeasureCreateFieldVO = fieldVOMap.get(f.getEnName());
            BeanUtils.copyProperties(originMeasureCreateFieldVO, col);
            col.setColumnEnName(originMeasureCreateFieldVO.getEnName());
            col.setLeafCategoryId(f.getLeafCategoryId());
            col.setColumnCnName(originMeasureCreateFieldVO.getCnName());
            col.setDataType(columnsMap.get(col.getColumnEnName()).getDataType());
            col.setDwTableId(modelVO.getId());
            dwColumnDTOList.add(col);
        });
        modelManager.syncColumns(modelVO, dwColumnDTOList);
        return Response.ok();
    }

    @Autowired
    private CategoryMapper categoryMapper;
    @Autowired
    private CategoryManager categoryManager;
    @Autowired
    private UserManager userManager;
    @Resource
    IMeasureApplicationService measureApplicationService;


    @GetMapping("/delete/{id}")
    @OperateLog
    @ApiOperation("模型删除接口")
    @ReloadCache
    public Response delete(@PathVariable Integer id) {
        modelManager.delete(id);
        return Response.ok();
    }

    @Resource
    IDimensionApplicationService dimensionApplicationService;

    @PostMapping("/list/all")
    @ApiOperation("模型列表接口")
    public Response<PageVO<ModelVO>> listModelWithoutPage(@RequestBody @Validated ModelQueryVO modelQueryVO) {
        Set<Integer> tableIds = new HashSet<>();
        if (modelQueryVO.getMeasId() != null) {
            // 过滤到已经配置过的模型
            List<MeasureApplication> applications = measureApplicationService.list(Wrappers.<MeasureApplication>lambdaQuery().eq(MeasureApplication::getMeasId, modelQueryVO.getMeasId()));
            Set<Integer> ids = applications.stream().filter(i -> Objects.nonNull(i.getDwTableId())).map(MeasureApplication::getDwTableId).collect(Collectors.toSet());
            if (!CollectionUtils.isEmpty(ids)) {
                tableIds.addAll(ids);
            }
        }

        if (modelQueryVO.getDimId() != null) {
            // 过滤到已经配置过的模型
            List<DimensionApplication> applications = dimensionApplicationService.list(Wrappers.<DimensionApplication>lambdaQuery().eq(DimensionApplication::getDimId, modelQueryVO.getDimId()));
            Set<Integer> ids = applications.stream().filter(i -> Objects.nonNull(i.getDwTableId())).map(DimensionApplication::getDwTableId).collect(Collectors.toSet());
            if (!CollectionUtils.isEmpty(ids)) {
                tableIds.addAll(ids);
            }
        }
        List<DwTable> records = dwTableMapper.selectList(
                Wrappers.<DwTable>lambdaQuery()
                        .and(!CollectionUtils.isEmpty(tableIds), qw -> qw.notIn(DwTable::getId, tableIds))
                        .like(StringUtils.hasLength(modelQueryVO.getKeyword()), DwTable::getCnName, modelQueryVO.getKeyword())
                        .or()
                        .like(StringUtils.hasLength(modelQueryVO.getKeyword()), DwTable::getEnName, modelQueryVO.getKeyword())
                        .orderByDesc(DwTable::getUpdateTime)
                        .last("limit 200"));

        List<ModelVO> resultList = records.stream().map(r -> modelManager.getModelVOByTable(r)).collect(Collectors.toList());
        return Response.ok(resultList);
    }


    @PostMapping("/list")
    @ApiOperation("模型列表接口")
    public Response<PageVO<ModelVO>> listModel(@RequestBody @Validated ModelPageQueryVO modelPageQueryVO) {
        List<Integer> leafCategoryIds = Optional.ofNullable(modelPageQueryVO.getCategoryId())
                .map(id -> categoryManager.findLeafIdById(id))
                .orElse(Collections.emptyList());

        List<Integer> userIds = userManager.getUserBySearchText(modelPageQueryVO.getKeyword()).stream().map(User::getId).collect(Collectors.toList());
        Page<DwTable> dwTablePage = dwTableMapper.selectPage(new Page<>(modelPageQueryVO.getPageNo(), modelPageQueryVO.getPageSize()),
                Wrappers.<DwTable>lambdaQuery()
                        .and(!CollectionUtils.isEmpty(leafCategoryIds), query -> query.in(!CollectionUtils.isEmpty(leafCategoryIds), DwTable::getLeafCategoryId, leafCategoryIds))
                        .and(StringUtils.hasLength(modelPageQueryVO.getKeyword()), query -> query.like(StringUtils.hasLength(modelPageQueryVO.getKeyword()), DwTable::getCnName, modelPageQueryVO.getKeyword())
                                .or()
                                .like(StringUtils.hasLength(modelPageQueryVO.getKeyword()), DwTable::getEnName, modelPageQueryVO.getKeyword())
                                .or()
                                .like(StringUtils.hasLength(modelPageQueryVO.getKeyword()), DwTable::getDescription, modelPageQueryVO.getKeyword())
                                .or()
                                .in(!CollectionUtils.isEmpty(userIds), DwTable::getCreator, userIds)
                                .or()
                                .in(!CollectionUtils.isEmpty(userIds), DwTable::getUpdater, userIds))

                        .orderByDesc(DwTable::getUpdateTime));

        List<DwTable> records = dwTablePage.getRecords();
        List<ModelVO> resultList = records.stream().map(r -> modelManager.getModelVOByTable(r)).collect(Collectors.toList());
        //获取分类信息
        List<Category> categoryList = categoryMapper.selectList(null);
        // Map:(categoryId,category)
        Set<Integer> measLeafCategoryIds = records.stream().map(DwTable::getLeafCategoryId).collect(Collectors.toSet());
        Map<Integer, List<CategoryVO>> measCategoryInfoMap = categoryManager.findParentsByLeaf(measLeafCategoryIds);
        resultList.forEach(m -> {
            m.setCategoryInfo(measCategoryInfoMap.get(m.getLeafCategoryId()));
        });
        PageVO<ModelVO> modelVOPageVO = new PageVO<ModelVO>(Long.valueOf(resultList.size()), resultList);
        return Response.ok(modelVOPageVO);
    }

    @GetMapping("/detail/{id}")
    public Response<ModelDetailVO> detail(@PathVariable("id") Integer id, @RequestParam(value = "traceId", required = false) String traceId) {

        ModelDetailVO detail = modelManager.detail(id);
        if (detail == null) {
            return Response.error("模型不存在");
        }
        return Response.ok(detail);
    }


    @SyncReloadCache
    @OperateLog
    @PostMapping("/update/fields")
    public Response updateModel(@RequestBody @Validated ModelUpdateVO modelUpdateVO) {
        modelManager.update(modelUpdateVO);
        return Response.ok();
    }

    @GetMapping("/list/field/{id}")
    public Response<List<ModelFieldVO>> listFiled(@PathVariable Integer id) {
        ModelDetailVO detail = modelManager.detail(id);
        return Response.ok(detail.getModelFieldList());
    }

    @Resource
    DimensionManager dimensionManager;

    @OperateLog
    @SyncReloadCache
    @PostMapping("/remove/fields")
    public Response removeField(@RequestBody @Validated ModelFieldRemove modelFieldRemove) {
        if (Objects.equals(modelFieldRemove.getFieldType(), TableColumnType.MEASURE.getCode())) {
            measureApplicationService.removeByIds(modelFieldRemove.getAppIds());
        } else if (Objects.equals(modelFieldRemove.getFieldType(), TableColumnType.DIMENSION.getCode())) {
            modelFieldRemove.getAppIds().forEach(id -> dimensionManager.removeApp(id));
        }
        return Response.ok();
    }
}
