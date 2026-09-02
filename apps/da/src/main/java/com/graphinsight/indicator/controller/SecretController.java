package com.graphinsight.indicator.controller;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import com.graphinsight.indicator.annotation.CheckCacheVersion;
import com.graphinsight.indicator.auto.entity.Category;
import com.graphinsight.indicator.auto.entity.DimContextRelation;
import com.graphinsight.indicator.auto.entity.Dimension;
import com.graphinsight.indicator.auto.entity.DimensionAnalysisTask;
import com.graphinsight.indicator.auto.entity.DimensionApplication;
import com.graphinsight.indicator.auto.entity.DwTable;
import com.graphinsight.indicator.auto.entity.Measure;
import com.graphinsight.indicator.auto.entity.MeasureApplication;
import com.graphinsight.indicator.auto.entity.MeasureNaturalDateMapping;
import com.graphinsight.indicator.auto.entity.OperateGrantConfig;
import com.graphinsight.indicator.auto.entity.TSuperAdmin;
import com.graphinsight.indicator.auto.entity.User;
import com.graphinsight.indicator.auto.mapper.CategoryMapper;
import com.graphinsight.indicator.auto.mapper.UserMapper;
import com.graphinsight.indicator.auto.service.ICategoryService;
import com.graphinsight.indicator.auto.service.IDimContextRelationService;
import com.graphinsight.indicator.auto.service.IDimensionAnalysisTaskService;
import com.graphinsight.indicator.auto.service.IDimensionApplicationService;
import com.graphinsight.indicator.auto.service.IDimensionService;
import com.graphinsight.indicator.auto.service.IDwTableService;
import com.graphinsight.indicator.auto.service.IMeasureNaturalDateMappingService;
import com.graphinsight.indicator.auto.service.IMeasureService;
import com.graphinsight.indicator.auto.service.IOperateGrantConfigService;
import com.graphinsight.indicator.auto.service.ITSuperAdminService;
import com.graphinsight.indicator.doris.entity.Columns;
import com.graphinsight.indicator.enums.MeasureType;
import com.graphinsight.indicator.job.MeasureSimilarityDataJob;
import com.graphinsight.indicator.manager.COALoginManager;
import com.graphinsight.indicator.manager.CacheManager;
import com.graphinsight.indicator.manager.CategoryManager;
import com.graphinsight.indicator.manager.DashboardManager;
import com.graphinsight.indicator.manager.DepartmentManager;
import com.graphinsight.indicator.manager.DimensionAnalysisManager;
import com.graphinsight.indicator.manager.DimensionAnalysisManagerV2;
import com.graphinsight.indicator.manager.DorisQueryManager;
import com.graphinsight.indicator.manager.HistogramManager;
import com.graphinsight.indicator.manager.MeasureSimilarityManager;
import com.graphinsight.indicator.manager.MetadataManager;
import com.graphinsight.indicator.manager.MysqlDumpManager;
import com.graphinsight.indicator.manager.OrganizationManager;
import com.graphinsight.indicator.manager.UserGrantContextManager;
import com.graphinsight.indicator.manager.UserManager;
import com.graphinsight.indicator.model.Response;
import com.graphinsight.indicator.model.cache.MeasureApplicationCache;
import com.graphinsight.indicator.model.cache.MeasureApplicationDependency;
import com.graphinsight.indicator.model.cache.MeasureCache;
import com.graphinsight.indicator.model.cache.MeasureDependencyTreeInfo;
import com.graphinsight.indicator.model.cache.MetadataCache;
import com.graphinsight.indicator.model.dto.ColumnCheckResult;
import com.graphinsight.indicator.model.dto.DimensionHistogramRequest;
import com.graphinsight.indicator.model.dto.HistogramInfo;
import com.graphinsight.indicator.model.dto.OperateGrantValue;
import com.graphinsight.indicator.model.dto.SimilarityResult;
import com.graphinsight.indicator.model.vo.BatchUpdateOperatorVO;
import com.graphinsight.indicator.model.vo.CategoryCreateVO;
import com.graphinsight.indicator.model.vo.CategorySeqUpdateVO;
import com.graphinsight.indicator.model.vo.ComplexMeasureRelyInfo;
import com.graphinsight.indicator.model.vo.DimensionAnalysisGiniQueryVO;
import com.graphinsight.indicator.model.vo.DimensionAnalysisTaskDetailVO;
import com.graphinsight.indicator.model.vo.MeasureCacheVO;
import com.graphinsight.indicator.model.vo.SimpleInfo;
import com.graphinsight.indicator.model.vo.UpdateItem;
import com.graphinsight.indicator.service.IndicatorService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.util.CollectionUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Date: 2022/3/7
 * Desc:
 */
@Slf4j
@RestController
@RequestMapping("/secret")
public class SecretController {

    @Autowired
    COALoginManager coaLoginManager;
    @Autowired
    UserMapper userMapper;
    @Autowired
    DepartmentManager departmentManager;
    @Autowired
    UserManager userManager;
    @Autowired
    IDimensionService dimensionService;
    @Autowired
    IMeasureService measureService;
    @Autowired
    IDwTableService dwTableService;
    @Autowired
    CacheManager cacheManager;
    @Autowired
    IOperateGrantConfigService operateGrantConfigService;
    @Autowired
    ITSuperAdminService superAdminService;
    @Autowired
    IDimContextRelationService dimContextRelationService;
    @Autowired
    DorisQueryManager dorisQueryManager;
    @Autowired
    MetadataManager metadataManager;
    @Autowired
    MysqlDumpManager mysqlDumpManager;

    @Autowired
    DashboardManager dashboardManager;
    @Resource
    HistogramManager histogramManager;
    @Resource
    IndicatorService indicatorService;


    @GetMapping("/test/column/test")
    public Response<List<ColumnCheckResult>> testTable() {
        MetadataCache metadataCache = cacheManager.getMetadataCache();
        Map<Integer, DwTable> dwTableMap = metadataCache.getDwTableMap();
        List<String> schemaNames = dwTableMap.values().stream().map(DwTable::getSchemaName).collect(Collectors.toList());
        List<String> tableNames = dwTableMap.values().stream().map(DwTable::getTableName).collect(Collectors.toList());
        List<Columns> columns = dorisQueryManager.listColumns(schemaNames, tableNames);
        Map<String, List<Columns>> columnMap = columns.stream().collect(Collectors.groupingBy(col -> col.getTableSchema() + "." + col.getTableName()));
        Map<Integer, DimensionApplication> dimensionApplicationMap = metadataCache.getDimensionApplicationMap();
        List<ColumnCheckResult> list = new ArrayList<>();
        Map<Integer, Measure> allMeasureMap = metadataCache.getAllMeasureMap();
        Map<Integer, Dimension> allDimensionMap = metadataCache.getAllDimensionMap();
        for (DimensionApplication dimensionApplication : dimensionApplicationMap.values()) {
            ColumnCheckResult result = new ColumnCheckResult();
            result.setName("维度应用表");
            String cnName = Optional.ofNullable(allDimensionMap.get(dimensionApplication.getDimId())).map(Dimension::getCnName).orElse(dimensionApplication.getDimId().toString());
            String userName = Optional.ofNullable(allDimensionMap.get(dimensionApplication.getDimId())).map(Dimension::getCreateUser).orElse("zhangxinran");
            result.setCreateUser(userName + "@graphinsight.com");
            result.setCnName(cnName);
            result.setAppId(dimensionApplication.getId());
            Integer dwTableId = dimensionApplication.getDwTableId();
            DwTable dwTable = dwTableMap.get(dwTableId);
            if (dwTable == null) {
                result.setMessage("事实表不存在");
                list.add(result);
                continue;
            }
            String key = dwTable.getSchemaName() + "." + dwTable.getTableName();
            List<Columns> columnsList = columnMap.get(key);
            if (CollectionUtils.isEmpty(columnsList)) {
                result.setMessage("事实表的字段列表为空");
                list.add(result);
                continue;
            }
            Set<String> cols = columnsList.stream().map(Columns::getColumnName).map(String::toLowerCase).collect(Collectors.toSet());
            String factColumn = dimensionApplication.getFactColumn();
            if (factColumn == null) {
                result.setMessage("维度应用表的factColumn为null");
                list.add(result);
                continue;
            }
            factColumn = factColumn.toLowerCase();
            if (factColumn.startsWith("date_format")) {
                factColumn = factColumn.substring(factColumn.indexOf("`") + 1, factColumn.lastIndexOf("`"));
            }
            if (!cols.contains(factColumn)) {
                result.setMessage("维度应用表factColumn:" + factColumn + "在SR事实表中不存在");
                list.add(result);
                continue;
            }
        }


        Map<Integer, MeasureApplication> measureApplicationMap = metadataCache.getMeasureApplicationMap();
        for (MeasureApplication dimensionApplication : measureApplicationMap.values()) {
            if (!Objects.equals(dimensionApplication.getApplyType().intValue(), MeasureType.ORIGIN.getCode().intValue())) {
                continue;
            }
            ColumnCheckResult result = new ColumnCheckResult();
            result.setName("指标应用表");
            String cnName = Optional.ofNullable(allMeasureMap.get(dimensionApplication.getMeasId())).map(Measure::getCnName).orElse(dimensionApplication.getMeasId().toString());
            String userName = Optional.ofNullable(allMeasureMap.get(dimensionApplication.getMeasId())).map(Measure::getCreateUser).orElse("zhangxinran");
            result.setCreateUser(userName + "@graphinsight.com");
            result.setCnName(cnName);
            result.setAppId(dimensionApplication.getId());
            Integer dwTableId = dimensionApplication.getDwTableId();
            DwTable dwTable = dwTableMap.get(dwTableId);

            if (dwTable == null) {
                result.setMessage("事实表不存在");
                list.add(result);
                continue;
            }
            String key = dwTable.getSchemaName() + "." + dwTable.getTableName();
            List<Columns> columnsList = columnMap.get(key);
            if (CollectionUtils.isEmpty(columnsList)) {
                result.setMessage("事实表的字段列表为空");
                list.add(result);
                continue;
            }
            Set<String> cols = columnsList.stream().map(Columns::getColumnName).map(String::toLowerCase).collect(Collectors.toSet());
            String factColumn = dimensionApplication.getFactColumn();
            if (factColumn == null){
                result.setMessage("维度应用表factColumn不存在");
                list.add(result);
                continue;
            }
            factColumn = factColumn.toLowerCase();
            if (!cols.contains(factColumn)) {
                result.setMessage("维度应用表factColumn:" + dimensionApplication.getFactColumn() + "在SR事实表中不存在");
                list.add(result);
                continue;
            }
        }
        return Response.ok(list);
    }

    @GetMapping("/test/histogram")
    public Response histogram() {
        histogramManager.runTable();
        histogramManager.runDimension();
        return Response.ok();
    }

    @GetMapping("/test/histogram/get")
    public Response getHistogram() {
        List<HistogramInfo> infos = indicatorService.listTableHistogram(Sets.newHashSet("eps_dw_rt.dwd_mfg_prod_equipment_fast_slow_charging_detection_rt"));
        DimensionHistogramRequest dimensionHistogramRequest = new DimensionHistogramRequest();
        dimensionHistogramRequest.setCode("DIM_b768029460e54a27b78767dcc1de0a96");
        dimensionHistogramRequest.setTableNames(Sets.newHashSet("eps_dw.dwd_sale_retail_ticket_follow_df"));
        List<HistogramInfo> dims = indicatorService.listDimensionHistogram(Lists.newArrayList(dimensionHistogramRequest));
        log.info("table -- {}", infos);
        log.info("dims -- {}", dims);
        return Response.ok();
    }


    @GetMapping("/test/dump")
    public Response dump() {
        mysqlDumpManager.dump();
//        cacheManager.syncReloadCache();
        return Response.ok();
    }

    @Resource
    IMeasureNaturalDateMappingService measureNaturalDateMappingService;

    @GetMapping("/dim/natural/wash")
    public Response washDirtyData() {
        List<MeasureNaturalDateMapping> list = measureNaturalDateMappingService.list(null);
        List<Dimension> dimensions = dimensionService.list(null);
        Map<Integer, Dimension> dimensionMap = dimensions.stream().collect(Collectors.toMap(Dimension::getId, dimension -> dimension));

        List<MeasureNaturalDateMapping> naturalDateMappings = list.stream().filter(mapping -> dimensionMap.get(mapping.getTargetDimId()) == null).collect(Collectors.toList());
        List<Long> ids = naturalDateMappings.stream().map(MeasureNaturalDateMapping::getId).collect(Collectors.toList());
        measureNaturalDateMappingService.removeByIds(ids);
        return Response.ok(ids);
    }


    @GetMapping("/sync/table/{id}")
    public Response syncColumns(@PathVariable("id") Long id) {
        DwTable table = dwTableService.getById(id);
        metadataManager.syncColumns(id, metadataManager.listColumns(table.getSchemaName(), table.getTableName()));
        return Response.ok();
    }


    @GetMapping("/mem/sort/test/{num}")
    public Response sortTest(@PathVariable("num") Long num) {
        dorisQueryManager.sortTest(num);
        return Response.ok();
    }

    @GetMapping("/dim/context/list")
    public Response dimContextList() {
        return Response.ok(dimContextRelationService.list());
    }

    @PostMapping("/dim/context/save")
    public Response dimContextSave(@RequestBody DimContextRelation dimContextRelation) {
        return Response.ok(dimContextRelationService.save(dimContextRelation));
    }

    @GetMapping("/dim/context/delete/{id}")
    public Response dimContextDel(@PathVariable("id") Long id) {
        return Response.ok(dimContextRelationService.removeById(id));
    }

    @PostMapping("/dim/context/update")
    public Response dimContextUpdate(@RequestBody DimContextRelation dimContextRelation) {
        return Response.ok(dimContextRelationService.updateById(dimContextRelation));
    }


    @GetMapping("/super/admin/list")
    public Response list() {
        return Response.ok(superAdminService.list());
    }

    @PostMapping("/super/admin/save")
    public Response save(@RequestBody TSuperAdmin tSuperAdmin) {
        return Response.ok(superAdminService.save(tSuperAdmin));
    }

    @GetMapping("/super/admin/delete/{id}")
    public Response delSuperAdmin(@PathVariable("id") Long id) {
        return Response.ok(superAdminService.removeById(id));
    }

    @PostMapping("/super/admin/update")
    public Response update(@RequestBody TSuperAdmin tSuperAdmin) {
        return Response.ok(superAdminService.updateById(tSuperAdmin));
    }

    @PostMapping("/operate/grant/save/config")
    public Response save(@RequestBody OperateGrantConfig operateGrantConfig) {
        return Response.ok(operateGrantConfigService.save(operateGrantConfig));
    }

    @GetMapping("/operate/grant/delete/{id}")
    public Response del(@PathVariable("id") Long id) {
        return Response.ok(operateGrantConfigService.removeById(id));
    }

    @PostMapping("/operate/grant/update")
    public Response update(@RequestBody OperateGrantConfig operateGrantConfig) {
        return Response.ok(operateGrantConfigService.updateById(operateGrantConfig));
    }

    @GetMapping("/get/measureCache/{id}")
    public Response getMeasureCacheInfo(@PathVariable Integer id) {
        MeasureCache measureCache = cacheManager.getMeasureCache(id);
        MetadataCache metadataCache = cacheManager.getMetadataCache();

        if (measureCache == null || metadataCache == null) {
            Response.error("缓存为空");
        }
        MeasureCacheVO measureCacheVO = new MeasureCacheVO();
        BeanUtils.copyProperties(measureCache, measureCacheVO);
        Set<Integer> relatedDimensionIds = measureCache.getRelatedDimensionIds();
        // 指标相关维度
        Map<Integer, Dimension> allDimensionMap = metadataCache.getAllDimensionMap();
        List<SimpleInfo> relatedDimensions = relatedDimensionIds.stream().map(dimId -> {
            Dimension dimension = allDimensionMap.get(dimId);
            SimpleInfo simpleInfo = new SimpleInfo();
            BeanUtils.copyProperties(dimension, simpleInfo);
            return simpleInfo;
        }).collect(Collectors.toList());
        measureCacheVO.setRelatedDimensions(relatedDimensions);
        // 指标相关模型
        Set<Integer> relatedDwTableIds = measureCache.getRelatedDwTableIds();
        Map<Integer, DwTable> dwTableMap = metadataCache.getDwTableMap();
        Map<Integer, Measure> allMeasureMap = metadataCache.getAllMeasureMap();
        List<SimpleInfo> dwTables = relatedDwTableIds.stream().map(tableId -> {
            DwTable dwTable = dwTableMap.get(tableId);
            SimpleInfo simpleInfo = new SimpleInfo();
            BeanUtils.copyProperties(dwTable, simpleInfo);
            return simpleInfo;
        }).collect(Collectors.toList());
        measureCacheVO.setRelatedModels(dwTables);
        List<MeasureApplicationCache> measureApplicationCacheList = measureCache.getMeasureApplicationCacheList();
        Map<Integer, List<MeasureApplicationCache>> measureAppCacheMap = measureApplicationCacheList.stream().collect(Collectors.groupingBy(MeasureApplicationCache::getMeasAppId));

        // 指标依赖关系
        List<MeasureDependencyTreeInfo> complexMeasureDependencyTrees = metadataCache.getComplexMeasureDependencyTrees();
        MeasureDependencyTreeInfo tree = complexMeasureDependencyTrees.stream().filter(c -> Objects.equals(c.getMeasId(), id)).findFirst().orElse(null);
        if (!Objects.isNull(tree)) {
            List<MeasureApplicationDependency> measureApplicationDependencyList = tree.getMeasureApplicationDependencyList();
            List<ComplexMeasureRelyInfo> measureRelyInfos = measureApplicationDependencyList.stream().map(mad -> {
                ComplexMeasureRelyInfo measureRelyInfo = new ComplexMeasureRelyInfo();
                measureRelyInfo.setMeasAppId(mad.getMeasAppId());
                measureRelyInfo.setRelyDimensions(mad.getDependencyDimIds().stream().map(dimId -> convert(allDimensionMap.get(dimId))).collect(Collectors.toList()));
                measureRelyInfo.setRelyBaseDimensions(mad.getDependencyBaseDimIds().stream().map(dimId -> convert(allDimensionMap.get(dimId))).collect(Collectors.toList()));
                measureRelyInfo.setRelyMeasures(mad.getDependencyMeasIds().stream().map(measId -> convert(allMeasureMap.get(measId))).filter(o -> Objects.nonNull(o)).collect(Collectors.toList()));
                measureRelyInfo.setRelyBaseMeasures(mad.getDependencyBaseMeasIds().stream().map(measId -> convert(allMeasureMap.get(measId))).filter(o -> Objects.nonNull(o)).collect(Collectors.toList()));
                List<MeasureApplicationCache> measureApplicationCaches = measureAppCacheMap.get(mad.getMeasAppId());
                if (!CollectionUtils.isEmpty(measureApplicationCaches)) {
                    //相关模型
                    measureRelyInfo.setRelatedDwTables(measureApplicationCaches.stream().map(mac -> convert(dwTableMap.get(mac.getRelatedDwTableId()))).collect(Collectors.toList()));
                }
                return measureRelyInfo;
            }).collect(Collectors.toList());
            measureCacheVO.setRelyInfos(measureRelyInfos);
        }
        return Response.ok(measureCacheVO);
    }

    private SimpleInfo convert(Object o) {
        SimpleInfo simpleInfo = new SimpleInfo();
        if (o == null) {
            return null;
        }
        BeanUtils.copyProperties(o, simpleInfo);
        return simpleInfo;
    }

    @PostMapping("/batch/update/operator")
    public Response batchUpdateOperator(@RequestBody BatchUpdateOperatorVO batchUpdateOperatorVO) {


        if (Objects.nonNull(batchUpdateOperatorVO.getDimensionUpdateItem())) {
            UpdateItem dimensionUpdateItem = batchUpdateOperatorVO.getDimensionUpdateItem();
            if (!CollectionUtils.isEmpty(dimensionUpdateItem.getIds()) && Objects.nonNull(dimensionUpdateItem.getCreator()) && Objects.nonNull(dimensionUpdateItem.getUpdater())) {
                List<Integer> ids = dimensionUpdateItem.getIds();
                List<Dimension> dimensionList = dimensionService.listByIds(ids);
                dimensionList.forEach(d -> {
                    d.setCreator(dimensionUpdateItem.getCreator());
                    d.setUpdater(dimensionUpdateItem.getUpdater());
                });
                dimensionService.updateBatchById(dimensionList);
            }
        }

        if (Objects.nonNull(batchUpdateOperatorVO.getMeasureUpdateItem())) {
            UpdateItem item = batchUpdateOperatorVO.getMeasureUpdateItem();
            if (!CollectionUtils.isEmpty(item.getIds()) && Objects.nonNull(item.getCreator()) && Objects.nonNull(item.getUpdater())) {
                List<Integer> ids = item.getIds();
                List<Measure> list = measureService.listByIds(ids);
                list.forEach(i -> {
                    i.setCreator(item.getCreator());
                    i.setUpdater(item.getUpdater());
                });
                measureService.updateBatchById(list);
            }
        }

        if (Objects.nonNull(batchUpdateOperatorVO.getModelUpdateItem())) {
            UpdateItem item = batchUpdateOperatorVO.getModelUpdateItem();
            if (!CollectionUtils.isEmpty(item.getIds()) && Objects.nonNull(item.getCreator()) && Objects.nonNull(item.getUpdater())) {
                List<Integer> ids = item.getIds();
                List<DwTable> list = dwTableService.listByIds(ids);
                list.forEach(i -> {
                    i.setCreator(item.getCreator());
                    i.setUpdater(item.getUpdater());
                });
                dwTableService.updateBatchById(list);
            }
        }

        return Response.ok();
    }

    @Autowired
    UserGrantContextManager userGrantContextManager;

    @GetMapping("/sync/user/{username}/{configId}")
    public Response getUserOperateContext(@PathVariable("username") String username,
                                          @PathVariable("configId") Long configId) {
        OperateGrantValue operateGrantValue = userGrantContextManager.getOperateGrantValue(username, configId);
        return Response.ok(operateGrantValue);
    }

    @GetMapping("/get/userContext/{username}/{spaceId}")
    public Response getUserContext(@PathVariable("username") String username,
                                   @PathVariable("spaceId") Long spaceId) {
        return Response.ok(userManager.getUserContext(spaceId, username));
    }


    @GetMapping("/sync/user/{jobNum}")
    public Response syncUserByJobNum(@PathVariable("jobNum") String jobNum) {
        User userInfo = coaLoginManager.getUserInfo(jobNum);
        if (Objects.nonNull(userInfo)) {
            userMapper.update(userInfo, Wrappers.<User>lambdaQuery().eq(User::getJobNumber, jobNum));
        } else {
            return Response.error("用户不存在");
        }
        return Response.ok();
    }

    @GetMapping("/sync/dept/")
    public Response syncDept() {
        departmentManager.syncDepartment();
        return Response.ok();
    }

    @GetMapping("/sync/user")
    public Response syncUser() {
        userManager.syncUser();
        return Response.ok();
    }

    @Autowired
    CategoryManager categoryManager;
    @Autowired
    ICategoryService categoryService;
    @Autowired
    CategoryMapper categoryMapper;
    @Autowired
    IDimensionApplicationService dimensionApplicationService;

    @PostMapping("/category/update")
    public Response<Category> update(@RequestBody CategoryCreateVO categoryCreateVO) {
        if (categoryCreateVO.getId() != null) {
            Category category = new Category();
            BeanUtils.copyProperties(categoryCreateVO, category);
            categoryMapper.updateById(category);
            return Response.ok(category);
        }
        return Response.ok("分类ID不存在");
    }

    @GetMapping("/del/dimApp/{id}")
    public Response delDimensionApplication(@PathVariable("id") Integer id) {
        dimensionApplicationService.removeById(id);
        return Response.ok();
    }

    @PostMapping("/category/sort")
    public Response<List<Category>> sort(@RequestBody CategorySeqUpdateVO categorySeqUpdateVO) {
        List<Category> needUpdateList = new ArrayList<>();
        if (!CollectionUtils.isEmpty(categorySeqUpdateVO.getIds())) {
            needUpdateList = categoryMapper.selectBatchIds(categorySeqUpdateVO.getIds());
            Map<Integer, Integer> map = new HashMap<>();
            for (int i = 0; i < categorySeqUpdateVO.getIds().size(); i++) {
                map.put(categorySeqUpdateVO.getIds().get(i), i);
            }
            needUpdateList.forEach(c -> {
                c.setSequence(map.get(c.getId()));
            });
        } else if (!CollectionUtils.isEmpty(categorySeqUpdateVO.getCnNames())) {
            needUpdateList = categoryMapper.selectList(Wrappers.<Category>lambdaQuery().in(Category::getName, categorySeqUpdateVO.getCnNames()));
            Map<String, Integer> map = new HashMap<>();
            for (int i = 0; i < categorySeqUpdateVO.getCnNames().size(); i++) {
                map.put(categorySeqUpdateVO.getCnNames().get(i), i);
            }
            needUpdateList.forEach(c -> {
                c.setSequence(map.get(c.getName()));
            });
        }
        if (!CollectionUtils.isEmpty(needUpdateList)) {
            categoryService.updateBatchById(needUpdateList);
        }
        return Response.ok();
    }

    @Autowired
    private DimensionAnalysisManagerV2 dimensionAnalysisManager2;
    @Autowired
    private IDimensionAnalysisTaskService taskService;

    @GetMapping("/test/analysis2")
    public Response testAnalysis2() {
        try {
            Long taskId = 214L;
            long start = System.currentTimeMillis();
            DimensionAnalysisTask analysisTask = taskService.getById(taskId);
            if (analysisTask == null) {
                // 任务不存在或者状态不是初始化，跳过处理
                return Response.ok();
            }
            DimensionAnalysisGiniQueryVO queryVO = new DimensionAnalysisGiniQueryVO();
            BeanUtils.copyProperties(analysisTask, queryVO);
            List<DimensionAnalysisTaskDetailVO> taskDetails = dimensionAnalysisManager2.queryGini(queryVO);
            long end = System.currentTimeMillis();
            log.info("执行完成，耗时:{} ms, 查询数量:{} ", end - start, taskDetails.size() + 2);
            Map<String, Object> map = new HashMap<>();
            map.put("耗时", end - start + "ms");
            map.put("查询数量", taskDetails.size() + 2);
            map.put("data", taskDetails);
            return Response.ok(map);
        } catch (Exception e) {
            log.error("多维分析任务执行失败:", e);
        }
        return Response.ok();
    }


    @Autowired
    @Qualifier("secondJdbcTemplate")
    private JdbcTemplate jdbcTemplate;

    @GetMapping("/datasource")
    public Response datasource() {
        jdbcTemplate.execute("select 1");
        return Response.ok();
    }


    @GetMapping("/checkAndSetCategoryCode")
    public Response checkAndSetCategoryCode() {
        categoryManager.checkAndSetCategoryCode();
        return Response.ok();
    }

    @GetMapping("/sync/dept/{deptId}")
    public Response syncUserByDeptId(@PathVariable("deptId") String deptId) {
        userManager.syncUser(deptId);
        return Response.ok();
    }

    @Autowired
    OrganizationManager organizationManager;
    @Autowired
    DimensionAnalysisManager dimensionAnalysisManager;

    @GetMapping("/sync/org/userNum")
    public Response syncOrgUserNum() {
        organizationManager.syncUserNum();
        return Response.ok();
    }


    @CheckCacheVersion
    @PostMapping("/mem/sort/demo")
    public Response memSortDemp() {
        Set<String> dimCodes = new HashSet<>();
        dimCodes.add("DIM_4504d234ebd640a382e7791d77761021");
        dimCodes.add("DIM_decb85f0c968466eb9b2bd5ae610bc86");
        dimCodes.add("DIM_3106a76897b5422fb57d77c1e9b85070");
        dimCodes.add("DIM_009c413e3a784437bd9f80177087975d");
        String measCode = "MEAS_3eeaaad17121497ab7ada30870a26a53";
        dimensionAnalysisManager.memSortDemo(dimCodes, measCode);
        return Response.ok();
    }

    @Resource
    MeasureSimilarityDataJob measureSimilarityDataJob;
    @Resource
    MeasureSimilarityManager measureSimilarityManager;

    @PostMapping("/measure/similarity/demo")
    public Response similarity() {
        measureSimilarityDataJob.runData();
        return Response.ok();
    }

    @PostMapping("/measure/similarity/get/{measCode}/{topN}")
    public Response getSimilarityR(@PathVariable("measCode") String measCode, @PathVariable("topN") Integer topN) {
        List<SimilarityResult> results = measureSimilarityManager.similarity(measCode, topN);
        return Response.ok(results);
    }


    @PostMapping("/measure/similarity/getByName/{measName}/{topN}")
    public Response getSimilarityRWithName(@PathVariable("measName") String measName, @PathVariable("topN") Integer topN) {
        Measure one = measureService.getOne(Wrappers.<Measure>lambdaQuery().eq(Measure::getCnName, measName));
        if (one == null) {
            return Response.ok();
        }
        List<SimilarityResult> results = measureSimilarityManager.similarity(one.getCode(), topN);
        return Response.ok(results);
    }

    // @GetMapping("/sync/dept/{idPath}")
    // public Response syncUserByJobNum(@PathVariable("idPath") String jobNum){
    //     User userInfo = coaLoginManager.getUserInfo(jobNum);
    //     if (Objects.nonNull(userInfo)){
    //         userMapper.update(userInfo, Wrappers.<User>lambdaQuery().eq(User::getJobNumber,jobNum));
    //     } else {
    //         return Response.error("用户不存在");
    //     }
    //     return Response.ok();
    // }
}
