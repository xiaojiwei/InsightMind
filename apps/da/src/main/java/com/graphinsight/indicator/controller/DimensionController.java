package com.graphinsight.indicator.controller;


import com.alibaba.fastjson.JSON;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.graphinsight.indicator.annotation.CheckCacheVersion;
import com.graphinsight.indicator.annotation.CurrentUser;
import com.graphinsight.indicator.annotation.OperateLog;
import com.graphinsight.indicator.annotation.ReloadCache;
import com.graphinsight.indicator.annotation.SyncReloadCache;
import com.graphinsight.indicator.auto.entity.Dimension;
import com.graphinsight.indicator.auto.entity.DimensionApplication;
import com.graphinsight.indicator.auto.entity.DimensionDimtableConnect;
import com.graphinsight.indicator.auto.entity.Level;
import com.graphinsight.indicator.auto.entity.Measure;
import com.graphinsight.indicator.auto.entity.User;
import com.graphinsight.indicator.auto.mapper.CategoryMapper;
import com.graphinsight.indicator.auto.mapper.DimensionDimtableConnectMapper;
import com.graphinsight.indicator.auto.mapper.DimensionMapper;
import com.graphinsight.indicator.auto.mapper.HierarchyMapper;
import com.graphinsight.indicator.auto.mapper.LevelMapper;
import com.graphinsight.indicator.auto.service.IDimensionApplicationService;
import com.graphinsight.indicator.enums.DimFrontType;
import com.graphinsight.indicator.enums.DimType;
import com.graphinsight.indicator.enums.YesNoType;
import com.graphinsight.indicator.exception.IndicatorParamNotValidException;
import com.graphinsight.indicator.manager.CacheManager;
import com.graphinsight.indicator.manager.CategoryManager;
import com.graphinsight.indicator.manager.DimensionManager;
import com.graphinsight.indicator.manager.MeasureManager;
import com.graphinsight.indicator.manager.ModelManager;
import com.graphinsight.indicator.manager.UserManager;
import com.graphinsight.indicator.model.ReferenceCheck;
import com.graphinsight.indicator.model.Response;
import com.graphinsight.indicator.model.dto.RelatedResourceDTO;
import com.graphinsight.indicator.model.vo.CategoryVO;
import com.graphinsight.indicator.model.vo.DimensionApplicationVO;
import com.graphinsight.indicator.model.vo.DimensionCreateVO;
import com.graphinsight.indicator.model.vo.DimensionQueryVO;
import com.graphinsight.indicator.model.vo.DimensionUpdateVO;
import com.graphinsight.indicator.model.vo.DimensionVO;
import com.graphinsight.indicator.model.vo.DimensionValuesCreateVO;
import com.graphinsight.indicator.model.vo.LevelVO;
import com.graphinsight.indicator.model.vo.MeasureVO;
import com.graphinsight.indicator.model.vo.OfflineRequest;
import com.graphinsight.indicator.model.vo.PageVO;
import com.graphinsight.indicator.service.impl.BuildSqlServiceImpl;
import com.graphinsight.indicator.util.IndicatorAssert;
import lombok.extern.slf4j.Slf4j;
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

import javax.annotation.Resource;
import java.sql.Timestamp;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * <p>
 * 指标表 前端控制器
 * </p>
 *
 * @since 2021-11-15
 */
@Slf4j
@RestController
@RequestMapping("/dimension")
public class DimensionController {

    @Autowired
    DimensionMapper dimensionMapper;
    @Autowired
    DimensionManager dimensionManager;
    @Autowired
    CategoryMapper categoryMapper;
    @Autowired
    ModelManager modelManager;
    @Autowired
    MeasureManager measureManager;
    @Autowired
    LevelMapper levelMapper;
    @Autowired
    private DimensionDimtableConnectMapper dimensionDimtableConnectMapper;
    @Autowired
    UserManager userManager;
    @Autowired
    HierarchyMapper hierarchyMapper;

    @OperateLog
    @SyncReloadCache
    @GetMapping("/online/{id}")
    public Response online(@PathVariable Integer id) {
        dimensionManager.online(id);
        return Response.ok();
    }

    @SyncReloadCache
    @PostMapping("/offline")
    public Response<List<ReferenceCheck>> offline(@RequestBody OfflineRequest request){
        List<ReferenceCheck> checks = dimensionManager.offline(request);
        if (!CollectionUtils.isEmpty(checks)){
            throw IndicatorParamNotValidException.error("有相关资源，不允许下线");
        }
        return Response.ok(checks);
    }

    @PostMapping("/offline/check")
    public Response<List<ReferenceCheck>> offlineCheck(@RequestBody OfflineRequest request){
        List<ReferenceCheck> checks = dimensionManager.offlineCheck(request);
        return Response.ok(checks);
    }

    @OperateLog
    @SyncReloadCache
    @PostMapping("/exp/save")
    public Response createExp(@Validated @RequestBody DimensionApplicationVO applicationVO) {
        dimensionManager.saveApplication(applicationVO);
        return Response.ok(applicationVO);
    }

    @OperateLog
    @SyncReloadCache
    @GetMapping("/exp/delete/{id}")
    public Response deleteExp(@PathVariable Integer id) {
        dimensionManager.deleteApplication(id);
        return Response.ok();
    }

    @OperateLog
    @SyncReloadCache
    @GetMapping("/exp/enable/{id}")
    public Response enableExp(@PathVariable Integer id) {
        dimensionManager.enableApplication(id);
        return Response.ok();
    }


    @OperateLog
    @SyncReloadCache
    @GetMapping("/exp/disable/{id}")
    public Response disableExp(@PathVariable Integer id) {
        List<RelatedResourceDTO> relatedResourceDTOS = dimensionManager.disableApplication(id);
        if (!CollectionUtils.isEmpty(relatedResourceDTOS)){
            String name = relatedResourceDTOS.stream().map(RelatedResourceDTO::getName).collect(Collectors.joining(","));
            throw IndicatorParamNotValidException.error("模型下线会导致相关资源：" + name + ", 失效，请先解除相关资源引用");
        }
        return Response.ok();
    }


    @OperateLog
    @ReloadCache
    @GetMapping("/delete/{id}")
    public Response deleteById(@PathVariable Integer id) {
        dimensionManager.deleteById(id);
        return Response.ok();
    }

    @GetMapping("/list/dimensionWithSameHierarchy/{id}")
    public Response listDimensionWithSameHierarchy(@PathVariable("id") Integer dimId) {
        List<Dimension> dimensions = dimensionManager.listDimensionWithSameHierarchy(dimId);
        return Response.ok(dimensions);
    }

    @OperateLog
    @ReloadCache
    @PostMapping("/values/save")
    public Response addDimensionValues(@Validated @RequestBody DimensionValuesCreateVO dimensionValuesCreateVO) {
        dimensionManager.saveDimensionValues(dimensionValuesCreateVO);
        return Response.ok();
    }


    @GetMapping("/list/dimensionWithSameDimTable/{id}")
    public Response<List<Dimension>> listDimensionWithSameDimTable(@PathVariable("id") Integer dimId) {
        List<Dimension> dimensions = dimensionManager.listDimensionWithSameDimTable(dimId);
        return Response.ok(dimensions);
    }


    @OperateLog
    @ReloadCache
    @PostMapping("/create")
    public Response createDimension(@CurrentUser User user, @RequestBody @Validated DimensionCreateVO dimensionVO) {
        String enName = dimensionVO.getEnName();
        Dimension dimension = dimensionMapper.selectOne(Wrappers.<Dimension>lambdaQuery().eq(Dimension::getEnName, enName));
        if (dimension != null) {
            return Response.error("英文名称重复");
        }
        dimensionManager.create(dimensionVO, user);
        return Response.ok();
    }

    @Resource
    IDimensionApplicationService dimensionApplicationService;

    @OperateLog
    @PostMapping("/update")
    @SyncReloadCache
    public Response updateDimension(@CurrentUser User user, @RequestBody @Validated DimensionUpdateVO dimensionUpdateVO) {
        if (Objects.equals(dimensionUpdateVO.getIsHyper(), YesNoType.YES)){
            // 检查维度是否已经关联了事实表
            List<DimensionApplication> dimensionApplications = dimensionApplicationService.list(Wrappers.<DimensionApplication>lambdaQuery().eq(DimensionApplication::getDimId, dimensionUpdateVO.getId()));
            IndicatorAssert.indicatorAssert(!CollectionUtils.isEmpty(dimensionApplications),"维度已关联了事实表，不能作为公共维度");
        }
        dimensionManager.update(dimensionUpdateVO, user);
        return Response.ok();
    }


    @Autowired
    CacheManager cacheManager;

    @CheckCacheVersion
    @GetMapping("/detail/{id}")
    public Response<DimensionVO> fetchDimension(@PathVariable("id") Integer id, @RequestParam(value = "traceId", required = false) String traceId) {
        Dimension dimension = dimensionMapper.selectById(id);
        DimensionDimtableConnect dimtableConnect = dimensionDimtableConnectMapper.selectOne(Wrappers.<DimensionDimtableConnect>lambdaQuery().eq(DimensionDimtableConnect::getDimId, id));
        DimensionVO dimensionVO = new DimensionVO();
        BeanUtils.copyProperties(dimension, dimensionVO);
        List<DimensionApplicationVO> applicationVOS = dimensionManager.fetchRelatedModel(id);
        log.info("相关模型:{}", JSON.toJSONString(applicationVOS));
        List<Measure> relatedMeas = dimensionManager.fetchRelatedMeas(id);
        List<Integer> measureIds = relatedMeas.stream().map(Measure::getId).collect(Collectors.toList());
        if (!CollectionUtils.isEmpty(measureIds)) {
            Set<Integer> leafCategoryIds = relatedMeas.stream().map(Measure::getLeafCategoryId).collect(Collectors.toSet());
            Map<Integer, List<CategoryVO>> categoryInfoMap = categoryManager.findParentsByLeaf(leafCategoryIds);
            Set<Integer> createBys = relatedMeas.stream().map(Measure::getCreator).collect(Collectors.toSet());
            createBys.addAll(relatedMeas.stream().map(Measure::getUpdater).collect(Collectors.toSet()));
            Map<Integer, User> userMap = userManager.getUserMapByIds(createBys);
            List<MeasureVO> measureVOList = relatedMeas.stream().map(measure -> {
                MeasureVO measureVO = new MeasureVO();
                BeanUtils.copyProperties(measure, measureVO);
                measureVO.setCreator(Optional.ofNullable(userMap)
                        .map(m -> m.get(measure.getCreator()))
                        .orElse(null));
                measureVO.setUpdater(Optional.ofNullable(userMap)
                        .map(m -> m.get(measure.getUpdater()))
                        .orElse(null));
                measureVO.setCreateTime(Timestamp.valueOf(measure.getCreateTime()).getTime());
                measureVO.setUpdateTime(Timestamp.valueOf(measure.getUpdateTime()).getTime());
                measureVO.setCategoryInfo(categoryInfoMap.get(measure.getLeafCategoryId()));
                measureVO.setMeasureExpressions(measureManager.getExpressionList(measure.getId()));
                return measureVO;
            }).collect(Collectors.toList());
            dimensionVO.setCreator(Optional.ofNullable(userManager.getUserById(dimension.getCreator()))
                    .orElse(null));
            dimensionVO.setRelatedMeasure(measureVOList);
        }
        // 获取维度级联信息
        Level level = levelMapper.selectOne(Wrappers.<Level>lambdaQuery().eq(Level::getDimId, id));
        if (level != null) {
            Integer hierarchyId = level.getHierarchyId();
            List<Level> levels = levelMapper.selectList(Wrappers.<Level>lambdaQuery().eq(Level::getHierarchyId, hierarchyId));
            if (!CollectionUtils.isEmpty(levels)) {
                List<LevelVO> levelVOList = levels.stream().sorted(Comparator.comparing(Level::getSequence)).map(l -> {
                    LevelVO levelVO = new LevelVO();
                    Dimension d = dimensionMapper.selectById(l.getDimId());
                    levelVO.setCnName(d.getCnName());
                    levelVO.setDimId(l.getDimId());
                    return levelVO;
                }).collect(Collectors.toList());
                dimensionVO.setLevels(levelVOList);
            }
        }

        dimensionVO.setDeveloper(userManager.getUserByName(dimension.getDeveloper()));
        dimensionVO.setCreator(userManager.getUserByName(dimension.getCreateUser()));
        dimensionVO.setUpdater(userManager.getUserByName(dimension.getUpdateUser()));
        dimensionVO.setDeveloper(userManager.getUserByName(dimension.getDeveloper()));
        dimensionVO.setCreateTime(Timestamp.valueOf(dimension.getCreateTime()).getTime());
        dimensionVO.setUpdateTime(Timestamp.valueOf(dimension.getUpdateTime()).getTime());
        dimensionVO.setCategoryInfo(categoryManager.findParentsByLeaf(dimension.getLeafCategoryId()));
        dimensionVO.setRelatedModel(applicationVOS);
        if (dimtableConnect != null) {
            dimensionVO.setQueryField(dimtableConnect.getDimPrimaryKey());
            dimensionVO.setDisplayField(dimtableConnect.getDimValueColumn());
            dimensionVO.setDimTableName(dimtableConnect.getDimTableName());
            dimensionVO.setWhereCondition(dimtableConnect.getWhereCondition());
            dimensionVO.setSchemaName(dimtableConnect.getSchemaName());
            dimensionVO.setFrontDimType(DimFrontType.DIM_WITH_TABLE.getCode());
        } else if (Objects.equals(DimType.DEGENERATE_DIM.getValue(), dimension.getDimType()) || Objects.isNull(dimension.getDimType())) {
            dimensionVO.setDisplayField(dimension.getEnName());
            dimensionVO.setQueryField(dimension.getEnName());
            dimensionVO.setFrontDimType(DimFrontType.DIM_WITHOUT_TABLE.getCode());
        } else if (Objects.equals(DimType.STD_WITHOUT_TABLE.getValue(), dimension.getDimType())) {
            dimensionVO.setDisplayField("v_value");
            dimensionVO.setQueryField("v_key");
            dimensionVO.setFrontDimType(DimFrontType.DIM_WITHOUT_TABLE.getCode());
        }


        return Response.ok(dimensionVO);
    }

    @Autowired
    private CategoryManager categoryManager;

    @PostMapping("/list")
    public Response<PageVO<DimensionVO>> listDimension(@RequestBody @Validated DimensionQueryVO dimensionQueryVO) {
        List<Integer> dimLeafCategoryIds = Optional.ofNullable(dimensionQueryVO.getCategoryId())
                .map(id -> categoryManager.findLeafIdById(id))
                .orElse(Collections.emptyList());
        List<Integer> userIds = userManager.getUserBySearchText(dimensionQueryVO.getKeyword()).stream().map(User::getId).collect(Collectors.toList());

        Page<Dimension> dimensionPage = dimensionMapper.selectPage(new Page<>(dimensionQueryVO.getPageNo(), dimensionQueryVO.getPageSize()),
                Wrappers.<Dimension>lambdaQuery()
                        .and(!CollectionUtils.isEmpty(dimLeafCategoryIds), queryMapper -> queryMapper.in(Dimension::getLeafCategoryId, dimLeafCategoryIds))
                        .and(Objects.nonNull(dimensionQueryVO.getIsHyper()), queryMapper -> queryMapper.eq(Dimension::getIsHyper, dimensionQueryVO.getIsHyper()))
                        .and(StringUtils.hasLength(dimensionQueryVO.getKeyword()), queryWrapper -> {
                            queryWrapper.like(StringUtils.hasLength(dimensionQueryVO.getKeyword()), Dimension::getCnName, dimensionQueryVO.getKeyword())
                                    .or()
                                    .like(StringUtils.hasLength(dimensionQueryVO.getKeyword()), Dimension::getEnName, dimensionQueryVO.getKeyword())
                                    .or()
                                    .like(StringUtils.hasLength(dimensionQueryVO.getKeyword()), Dimension::getDescription, dimensionQueryVO.getKeyword())
                                    .or()
                                    .eq(StringUtils.hasLength(dimensionQueryVO.getKeyword()), Dimension::getCode, dimensionQueryVO.getKeyword())
                                    .or()
                                    .in(!CollectionUtils.isEmpty(userIds), Dimension::getCreator, userIds)
                                    .or()
                                    .in(!CollectionUtils.isEmpty(userIds), Dimension::getUpdater, userIds);
                        })
                        .orderByDesc(Dimension::getUpdateTime));

        List<Dimension> records = dimensionPage.getRecords();
        List<DimensionVO> resultList = dimensionManager.listDimension(records);
        PageVO<DimensionVO> dimensionPageVO = new PageVO<>(dimensionPage.getTotal(), resultList);
        return Response.ok(dimensionPageVO);
    }
}
