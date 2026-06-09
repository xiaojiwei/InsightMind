package com.graphinsight.indicator.controller;


import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.graphinsight.indicator.annotation.CheckCacheVersion;
import com.graphinsight.indicator.annotation.CurrentUser;
import com.graphinsight.indicator.annotation.OperateLog;
import com.graphinsight.indicator.annotation.ReloadCache;
import com.graphinsight.indicator.annotation.SyncReloadCache;
import com.graphinsight.indicator.auto.entity.*;
import com.graphinsight.indicator.auto.mapper.*;
import com.graphinsight.indicator.auto.service.IMeasureNaturalDateMappingService;
import com.graphinsight.indicator.auto.service.IMeasureService;
import com.graphinsight.indicator.enums.MeasureType;
import com.graphinsight.indicator.exception.IndicatorParamNotValidException;
import com.graphinsight.indicator.manager.CacheManager;
import com.graphinsight.indicator.manager.CategoryManager;
import com.graphinsight.indicator.manager.DimensionManager;
import com.graphinsight.indicator.manager.MeasureManager;
import com.graphinsight.indicator.manager.ModelManager;
import com.graphinsight.indicator.manager.UserManager;
import com.graphinsight.indicator.model.ReferenceCheck;
import com.graphinsight.indicator.model.Response;
import com.graphinsight.indicator.model.vo.CategoryVO;
import com.graphinsight.indicator.model.vo.ComplexMeasureBaseVO;
import com.graphinsight.indicator.model.vo.ComplexMeasureCreateVO;
import com.graphinsight.indicator.model.vo.ComplexMeasureUpdateVO;
import com.graphinsight.indicator.model.vo.DimensionFilterCreateVO;
import com.graphinsight.indicator.model.vo.DimensionVO;
import com.graphinsight.indicator.model.vo.ExpressionItem;
import com.graphinsight.indicator.model.vo.MeasureCreateVO;
import com.graphinsight.indicator.model.vo.MeasureOnlineCheck;
import com.graphinsight.indicator.model.vo.MeasureQueryVO;
import com.graphinsight.indicator.model.vo.MeasureUpdateVO;
import com.graphinsight.indicator.model.vo.MeasureVO;
import com.graphinsight.indicator.model.vo.ModelVO;
import com.graphinsight.indicator.model.vo.NaturalDimConfigQueryVO;
import com.graphinsight.indicator.model.vo.OfflineRequest;
import com.graphinsight.indicator.model.vo.OriginMeasureCreateVO;
import com.graphinsight.indicator.model.vo.PageVO;
import com.graphinsight.indicator.model.vo.SummedUpDimensionQueryVO;
import com.graphinsight.indicator.util.BuildSqlUtil;
import com.graphinsight.indicator.util.IndicatorAssert;
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

import java.sql.Timestamp;
import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * <p>
 * 指标表 前端控制器
 * </p>
 *
 * @author lixiaolong
 * @since 2021-11-15
 */
@RestController
@RequestMapping("/measure")
public class MeasureController {

    @Autowired
    MeasureManager measureManager;
    @Autowired
    IMeasureService measureService;
    @Autowired
    MeasureMapper measureMapper;
    @Autowired
    CategoryMapper categoryMapper;
    @Autowired
    ModelManager modelManager;
    @Autowired
    DimensionManager dimensionManager;
    @Autowired
    CacheManager cacheManager;
    @Autowired
    IMeasureNaturalDateMappingService naturalDateMappingService;

    @SyncReloadCache
    @GetMapping("/online/{id}")
    @ApiOperation("指标上线")
    public Response<MeasureOnlineCheck> online(@PathVariable Integer id){
        MeasureOnlineCheck check = measureManager.online(id);
        return Response.ok(check);
    }

    @GetMapping("/online/check/{id}")
    @ApiOperation("指标上线")
    public Response<MeasureOnlineCheck> onlineCheck(@PathVariable Integer id){
        Measure measure = measureService.getById(id);
        IndicatorAssert.indicatorAssert(measure == null, "指标不存在,ID:" + id);
        MeasureOnlineCheck onlineCheck = measureManager.checkOnlinable(measure);
        return Response.ok(onlineCheck);
    }

    @SyncReloadCache
    @PostMapping("/offline")
    @ApiOperation("指标下线")
    public Response<List<ReferenceCheck>> offline(@RequestBody OfflineRequest request){
        List<ReferenceCheck> checks = measureManager.offline(request);
        if (!CollectionUtils.isEmpty(checks)){
            throw IndicatorParamNotValidException.error("有相关资源，不允许下线");
        }
        return Response.ok(checks);
    }

    @PostMapping("/offline/check")
    @ApiOperation("指标下线检查接口")
    public Response<List<ReferenceCheck>> offlineCheck(@RequestBody OfflineRequest request){
        List<ReferenceCheck> checks = measureManager.offlineCheck(request);
        return Response.ok(checks);
    }

    /**
     * 获取可配置归总的维度列表
     *
     * @return
     */
    @PostMapping("/list/sumable/dimension")
    @ApiOperation("获取可归总的维度列表")
    @CheckCacheVersion
    public Response<List<Dimension>> listCanBeSummedUpDimension(@Validated @RequestBody SummedUpDimensionQueryVO summedUpDimensionQueryVO) {
        List<Dimension> dimensionList = measureManager.listCanBeSummedUpDimension(summedUpDimensionQueryVO);
        return Response.ok(dimensionList);
    }

    @OperateLog
    @ReloadCache
    @GetMapping("/delete/{id}")
    public Response deleteById(@PathVariable Integer id) {
        measureManager.deleteById(id);
        return Response.ok();
    }


    @PostMapping("/natural/dim/echo")
    @ApiOperation("维度归总配置回显")
    public Response<List<Dimension>> naturalDimConfig(@Validated @RequestBody NaturalDimConfigQueryVO queryVO) {
        List<MeasureNaturalDateMapping> measureNaturalDateMappings = naturalDateMappingService
                .list(Wrappers.<MeasureNaturalDateMapping>lambdaQuery()
                        .eq(MeasureNaturalDateMapping::getMeasId, queryVO.getMeasId())
                        .eq(MeasureNaturalDateMapping::getDwTableId, queryVO.getModelId()));
        if (measureNaturalDateMappings != null){
            List<Dimension> dimensions = measureNaturalDateMappings.stream().map(mapping -> {
                Long targetDimId = mapping.getTargetDimId();
                Dimension dimension = dimensionMapper.selectById(targetDimId);
                return dimension;
            }).collect(Collectors.toList());
            return Response.ok(dimensions);
        }
        return Response.ok();
    }

    @OperateLog
    @SyncReloadCache
    @PostMapping("/origin/create")
    public Response createOriginMeasure(@Validated @RequestBody OriginMeasureCreateVO measureCreateVO) {
        boolean nameRepeat = measureManager.measureNameRepeat(measureCreateVO.getEnName(), measureCreateVO.getCnName());
        if (nameRepeat) {
            throw IndicatorParamNotValidException.error("中文名或者英文名重复");
        }
        if (StringUtils.hasLength(measureCreateVO.getWhereCondition())) {
            String whereCondition = PATTERN.matcher(measureCreateVO.getWhereCondition()).replaceAll("");
            if (BuildSqlUtil.containsSqlInjection(whereCondition)) {
                throw IndicatorParamNotValidException.error("where条件不合法");
            }
            measureCreateVO.setWhereCondition(whereCondition);
        }
        Response response = measureManager.saveOrUpdateOriginMeasure(measureCreateVO);
        return Response.ok(response);
    }

    private static final Pattern PATTERN = Pattern.compile("\\n|\\t|\\r");

    @OperateLog
    @SyncReloadCache
    @PostMapping("/origin/update")
    public Response updateOriginMeasure(@Validated @RequestBody OriginMeasureCreateVO measureCreateVO) {
        if (Objects.isNull(measureCreateVO.getId())) {
            throw IndicatorParamNotValidException.error("主键不能为空");
        }
        if (StringUtils.hasLength(measureCreateVO.getWhereCondition())) {
            String whereCondition = PATTERN.matcher(measureCreateVO.getWhereCondition()).replaceAll("");
            if (BuildSqlUtil.containsSqlInjection(whereCondition)) {
                throw IndicatorParamNotValidException.error("where条件不合法");
            }
            measureCreateVO.setWhereCondition(whereCondition);
        }
        Response response = measureManager.saveOrUpdateOriginMeasure(measureCreateVO);
        return Response.ok(response);
    }

    @OperateLog
    @SyncReloadCache
    @ApiOperation("创建计算指标或者给现有计算指标添加新的计算表达式.判断创建指标还是创建表达式的依据是英文名是否存在")
    @PostMapping("/complex/create")
    public Response createComplexMeasure(@Validated @RequestBody ComplexMeasureCreateVO measureCreateVO) {
        boolean nameRepeat = measureManager.measureNameRepeat(measureCreateVO.getEnName(), measureCreateVO.getCnName());
        if (nameRepeat) {
            throw IndicatorParamNotValidException.error("中文名或者英文名重复");
        }
        checkParam(measureCreateVO);
        measureManager.createComplexMeasure(measureCreateVO);
        return Response.ok();
    }

    @OperateLog
    @SyncReloadCache
    @ApiOperation("更新现有计算指标表达式")
    @PostMapping("/complex/update")
    public Response updateComplexMeasure(@Validated @RequestBody ComplexMeasureUpdateVO measureUpdateVO) {
        checkParam(measureUpdateVO);
        measureManager.updateComplexMeasure(measureUpdateVO);
        return Response.ok();
    }

    private void checkParam(ComplexMeasureBaseVO measureBaseVO) {
        if (StringUtils.hasLength(measureBaseVO.getWhereCondition())) {
            if (BuildSqlUtil.containsSqlInjection(measureBaseVO.getWhereCondition())) {
                throw IndicatorParamNotValidException.error("where条件不合法");
            }
        }
        LinkedList<ExpressionItem> expressionItemList = measureBaseVO.getExpressionItemList();
        if (CollectionUtils.isEmpty(expressionItemList)) {
            throw IndicatorParamNotValidException.error("表达式列表不能为空");
        }

        // 默认是复合指标
        measureBaseVO.setMeasureType(MeasureType.DERIVED.getCode());
        LinkedList<DimensionFilterCreateVO> dimensionFilterList = measureBaseVO.getDimensionFilterList();
        if (!CollectionUtils.isEmpty(dimensionFilterList)) {
            // 只要配置了维度过滤器，就是派生指标
            measureBaseVO.setMeasureType(MeasureType.EXTENDED.getCode());
        }
    }

    @OperateLog
    @ReloadCache
    @PostMapping("/create")
    public Response createMeasure(@RequestBody @Validated MeasureCreateVO measureVO) {
        List<Measure> measures = measureMapper.selectList(Wrappers.<Measure>lambdaQuery()
                .eq(Measure::getEnName, measureVO.getEnName())
                .or()
                .eq(Measure::getCnName, measureVO.getCnName()));
        if (!CollectionUtils.isEmpty(measures)) {
            return Response.error("中文名或英文名重复");
        }
        measureManager.create(measureVO);
        return Response.ok();
    }

    @OperateLog
    @PostMapping("/update")
    @ReloadCache
    public Response updateMeasure(@ApiIgnore @CurrentUser User user, @RequestBody @Validated MeasureUpdateVO measureUpdateVO) {
        List<Measure> measures = measureService.list(Wrappers.<Measure>lambdaQuery()
                .and(query -> query.ne(Measure::getId, measureUpdateVO.getId()))
                .and(query ->
                        query.eq(Measure::getEnName, measureUpdateVO.getEnName())
                                .or()
                                .eq(Measure::getCnName, measureUpdateVO.getCnName())));
        if (!CollectionUtils.isEmpty(measures)) {
            return Response.error("中文名或英文名重复");
        }
        measureManager.update(measureUpdateVO, user);
        return Response.ok();
    }

    @Autowired
    CategoryManager categoryManager;
    @Autowired
    UserManager userManager;
    @Autowired
    DimensionMapper dimensionMapper;
    @Autowired
    WordValuesMapper wordValuesMapper;

    @CheckCacheVersion
    @GetMapping("/detail/{id}")
    public Response<MeasureVO> fetchMeasure(@PathVariable("id") Integer id, @RequestParam(value = "traceId", required = false) String traceId) {
        Measure measure = measureMapper.selectById(id);
        if (Objects.isNull(measure)) {
            return Response.error("指标不存在");
        }

        MeasureVO measureVO = new MeasureVO();
        BeanUtils.copyProperties(measure, measureVO);
        Department department = departmentMapper.selectOne(Wrappers.<Department>lambdaQuery().eq(Department::getDepartmentId, measure.getDepartmentId()));
        measureVO.setDepartment(department);
        measureVO.setDeptLevel(department == null ? null : department.getDeptLevel());
        measureVO.setCategoryInfo(categoryManager.findParentsByLeaf(measure.getLeafCategoryId()));
        List<ModelVO> modelVOs = measureManager.fetchRelatedModelFromCache(id).stream().sorted(Comparator.comparing(ModelVO::getId)).collect(Collectors.toList());
        List<Dimension> dimensions = measureManager.fetchRelatedDimensionFromCache(id);
        List<Integer> dimIds = dimensions.stream().map(Dimension::getId).collect(Collectors.toList());
        if (!CollectionUtils.isEmpty(dimIds)) {
            Set<Integer> dimLeafCategoryIds = dimensions.stream().map(Dimension::getLeafCategoryId).collect(Collectors.toSet());
            Map<Integer, List<CategoryVO>> dimCategoryInfoMap = categoryManager.findParentsByLeaf(dimLeafCategoryIds);
            Set<Integer> createBys = dimensions.stream().map(Dimension::getCreator).collect(Collectors.toSet());
            createBys.addAll(dimensions.stream().map(Dimension::getUpdater).collect(Collectors.toSet()));
            Map<Integer, User> userMap = userManager.getUserMapByIds(createBys);
            List<DimensionVO> dimensionVOs = dimensions.stream().map(dimension -> {
                DimensionVO dimensionVO = new DimensionVO();
                BeanUtils.copyProperties(dimension, dimensionVO);
                dimensionVO.setCreator(Optional.ofNullable(userMap.get(dimension.getCreator()))
                        .orElse(null));
                dimensionVO.setUpdater(Optional.ofNullable(userMap.get(dimension.getUpdater()))
                        .orElse(null));
                dimensionVO.setCreateTime(Timestamp.valueOf(dimension.getCreateTime()).getTime());
                dimensionVO.setUpdateTime(Timestamp.valueOf(dimension.getUpdateTime()).getTime());
                dimensionVO.setCategoryInfo(dimCategoryInfoMap.get(dimension.getLeafCategoryId()));
                return dimensionVO;
            }).sorted(Comparator.comparing(DimensionVO::getId)).collect(Collectors.toList());
            measureVO.setRelatedDimension(dimensionVOs);
        }
        measureVO.setCreator(userManager.getUserByName(measure.getCreateUser()));
        measureVO.setOwner(userManager.getUserByUsername(measure.getOwnerUser()));
        measureVO.setDeveloper(userManager.getUserByName(measure.getDevelopUser()));
        measureVO.setUpdater(userManager.getUserByName(measure.getUpdateUser()));
        measureVO.setCreateTime(Timestamp.valueOf(measure.getCreateTime()).getTime());
        measureVO.setUpdateTime(Timestamp.valueOf(measure.getUpdateTime()).getTime());
        measureVO.setMeasureExpressions(measureManager.getExpressionListFromDB(id));
        measureVO.setRelatedModel(modelVOs);

        List<WordValues> wordValuesList = wordValuesMapper.selectValueList(Arrays.asList(measureVO.getCnName()));
        measureVO.setAliases(wordValuesList.stream().map(WordValues::getValue).collect(Collectors.toList()));

        return Response.ok(measureVO);
    }


    @Autowired
    DepartmentMapper departmentMapper;


    @PostMapping("/list")
    public Response<PageVO<MeasureVO>> listMeasure(@RequestBody @Validated MeasureQueryVO measureQueryVO) {
        List<Integer> measLeafCategoryIds = Optional.ofNullable(measureQueryVO.getCategoryId())
                .map(id -> categoryManager.findLeafIdById(id))
                .orElse(Collections.emptyList());

        List<Integer> scopes = measureQueryVO.getDeptLevels();
        List<Integer> deptIds = Collections.EMPTY_LIST;
        if (!CollectionUtils.isEmpty(scopes)) {
            List<Department> departments = departmentMapper.selectList(Wrappers.<Department>lambdaQuery().in(Department::getFeishuDeptId, scopes));
            if (!CollectionUtils.isEmpty(departments)) {
                deptIds = departments.stream().map(Department::getDepartmentId).collect(Collectors.toList());
            }
        }
        List<Integer> userIds = userManager.getUserBySearchText(measureQueryVO.getKeyword()).stream().map(User::getId).collect(Collectors.toList());

        Page<Measure> measurePage = measureMapper.selectPage(new Page<>(measureQueryVO.getPageNo(), measureQueryVO.getPageSize()),
                Wrappers.<Measure>lambdaQuery()
                        .and(!CollectionUtils.isEmpty(measLeafCategoryIds), query -> query.in(Measure::getLeafCategoryId, measLeafCategoryIds))
                        .in(!CollectionUtils.isEmpty(deptIds), Measure::getDepartmentId, deptIds)
                        .and(StringUtils.hasLength(measureQueryVO.getKeyword()), query -> query.like(StringUtils.hasLength(measureQueryVO.getKeyword()), Measure::getCnName, measureQueryVO.getKeyword())
                                .or()
                                .like(StringUtils.hasLength(measureQueryVO.getKeyword()), Measure::getEnName, measureQueryVO.getKeyword())
                                .or()
                                .like(StringUtils.hasLength(measureQueryVO.getKeyword()), Measure::getDescription, measureQueryVO.getKeyword())
                                .or()
                                .eq(StringUtils.hasLength(measureQueryVO.getKeyword()), Measure::getCode, measureQueryVO.getKeyword())
                                .or()
                                .in(!CollectionUtils.isEmpty(userIds), Measure::getOwner, userIds)
                                .or()
                                .in(!CollectionUtils.isEmpty(userIds), Measure::getCreator, userIds)
                                .or()
                                .in(!CollectionUtils.isEmpty(userIds), Measure::getUpdater, userIds)
                                .or()
                                .in(!CollectionUtils.isEmpty(userIds), Measure::getDeveloper, userIds))
                        .orderByDesc(Measure::getUpdateTime));

        List<Measure> records = measurePage.getRecords();
        List<Integer> measureIds = records.stream().map(Measure::getId).collect(Collectors.toList());
        Set<Integer> deparmentIds = records.stream().map(Measure::getDepartmentId).collect(Collectors.toSet());
        Map<Integer, Department> departmentMap = new HashMap<>();
        if (!CollectionUtils.isEmpty(deparmentIds)) {
            departmentMap = departmentMapper.selectBatchIds(deparmentIds).stream().collect(Collectors.toMap(Department::getDepartmentId, d -> d));
        }
        if (CollectionUtils.isEmpty(measureIds)) {
            measureIds.add(-1);
        }
        Map<String, User> userMap = userManager.getAllUserMap();
        //获取分类信息
        // 获取所有分类
        List<Category> categoryList = categoryMapper.selectList(null);
        // Map:(categoryId,category)
        List<MeasureVO> resultList = new ArrayList<>(records.size());
        Set<Integer> leafCategoryIds = records.stream().map(Measure::getLeafCategoryId).collect(Collectors.toSet());
        Map<Integer, List<CategoryVO>> measCategoryInfoMap = categoryManager.findParentsByLeaf(leafCategoryIds);
        for (Measure measure : records) {
            MeasureVO measureVO = new MeasureVO();
            BeanUtils.copyProperties(measure, measureVO);
            measureVO.setCreator(userMap.get(measure.getCreateUser()));
            measureVO.setUpdater(userMap.get(measure.getUpdateUser()));
            measureVO.setDeveloper(userMap.get(measure.getDevelopUser()));
            measureVO.setOwner(userMap.get(measure.getOwnerUser()));
            measureVO.setCategoryInfo(measCategoryInfoMap.get(measure.getLeafCategoryId()));
            measureVO.setCreateTime(Timestamp.valueOf(measure.getCreateTime()).getTime());
            measureVO.setUpdateTime(Timestamp.valueOf(measure.getUpdateTime()).getTime());
            measureVO.setDepartment(departmentMap.get(measure.getCode()));
            resultList.add(measureVO);
        }
        PageVO<MeasureVO> measurePageVO = new PageVO<>(measurePage.getTotal(), resultList);
        return Response.ok(measurePageVO);
    }

}
