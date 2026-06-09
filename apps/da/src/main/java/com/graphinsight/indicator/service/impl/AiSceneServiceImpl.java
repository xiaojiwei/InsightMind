package com.graphinsight.indicator.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.graphinsight.indicator.auto.entity.*;
import com.graphinsight.indicator.auto.mapper.*;
import com.graphinsight.indicator.auto.service.ITSpaceService;
import com.graphinsight.indicator.auto.service.ITSuperAdminService;
import com.graphinsight.indicator.enums.SceneType;
import com.graphinsight.indicator.enums.ViewType;
import com.graphinsight.indicator.manager.*;
import com.graphinsight.indicator.model.cache.MetadataCache;
import com.graphinsight.indicator.model.dto.MeasLabelGroupDTO;
import com.graphinsight.indicator.model.dto.UserContext;
import com.graphinsight.indicator.model.vo.*;
import com.graphinsight.indicator.service.AiSceneService;
import com.graphinsight.indicator.util.UserThreadLocalUtil;
import io.swagger.models.auth.In;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;
import org.springframework.validation.annotation.Validated;

import java.sql.Timestamp;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Configuration
@EnableScheduling
@Slf4j
public class AiSceneServiceImpl implements AiSceneService {


    @Autowired
    ITSuperAdminService superAdminService;
    @Autowired
    private UserManager userManager;

    @Autowired
    BloodManager bloodManager;

    @Autowired
    TSpaceMapper tSpaceMapper;
    @Autowired
    DaMeasLabelMapper daMeasLabelMapper;

    @Autowired
    MeasureManager measureManager;

    @Autowired
    CacheManager cacheManager;

    @Autowired
    DaIndicatorLabelMapper daIndicatorLabelMapper;

    @Value("${default.label.name:全国房屋挂牌数据}")
    String defaultLabelName;

    @Override
    public List<AiSceneInfoVo> sceneList() {
        List<AiSceneInfoVo> aiSceneInfoVoList = new ArrayList<>();
        // 获取指标信息 包含有权限 无权限
        List<CategoryNodeItem> aiSceneInfoItems = listMeasureByCurUser();

        Map<Integer, CategoryNodeItem> aiSceneInfoItemMap = new HashMap<>();
        List<Integer> authMeasureIds = new ArrayList<>();
        for (CategoryNodeItem aiSceneInfoItem : aiSceneInfoItems) {
            if (aiSceneInfoItem.isHasAuth()) {
                authMeasureIds.add(aiSceneInfoItem.getId());
            }
            aiSceneInfoItemMap.put(aiSceneInfoItem.getId(), aiSceneInfoItem);
        }

        if (authMeasureIds.isEmpty()) {
            return aiSceneInfoVoList;
        }

        List<MeasLabelGroupDTO> measLabelGroupDTOList = daMeasLabelMapper.getRelateMeasGroupLabel(authMeasureIds);
        // 获取标签名称
        List<DaIndicatorLabel> daIndicatorLabels = daIndicatorLabelMapper.selectList(Wrappers.<DaIndicatorLabel>lambdaQuery()
                .in(DaIndicatorLabel::getId, measLabelGroupDTOList.stream().map(MeasLabelGroupDTO::getLabelId).collect(Collectors.toList()))
                .orderByDesc(DaIndicatorLabel::getCreateTime)
                .last("LIMIT 4"));

        aiSceneInfoVoList = groupByLabel(measLabelGroupDTOList, daIndicatorLabels, aiSceneInfoItemMap, true);

        return aiSceneInfoVoList;
    }


    @Override
    public List<AiSceneInfoVo> marketSceneList(AiMarkerDetailVo aiSceneDetailVo) {

        // 获取指标信息 包含有权限 无权限
        List<CategoryNodeItem> aiSceneInfoItems = listMeasureByCurUser();
        Map<Integer, CategoryNodeItem> aiSceneInfoItemMap = aiSceneInfoItems.stream().collect(Collectors.toMap(CategoryNodeItem::getId, Function.identity(), (ex, re) -> ex));

        List<MeasLabelGroupDTO> measLabelGroupDTOList = daMeasLabelMapper.getRelateMeasGroupLabel(null);
        // 获取标签名称
        List<DaIndicatorLabel> daIndicatorLabels = daIndicatorLabelMapper.selectList(Wrappers.<DaIndicatorLabel>lambdaQuery()
                .in(DaIndicatorLabel::getId, measLabelGroupDTOList.stream().map(MeasLabelGroupDTO::getLabelId).collect(Collectors.toList()))
                .orderByDesc(DaIndicatorLabel::getId));
        Map<Long, DaIndicatorLabel> daIndicatorLabelMap = daIndicatorLabels.stream().collect(Collectors.toMap(DaIndicatorLabel::getId, Function.identity(), (ex, re) -> ex));

        Set<Integer> labelMeasureIds = measLabelGroupDTOList.stream().flatMap(str -> Arrays.stream(str.getMeasIds().split(","))).map(Integer::valueOf).collect(Collectors.toSet());

        List<AiSceneInfoVo> aiSceneInfoVoList = groupByLabel(measLabelGroupDTOList, daIndicatorLabels, aiSceneInfoItemMap, false);

        // 处理未分类
        AiSceneInfoVo aiSceneInfoNoLableVo = new AiSceneInfoVo();
        aiSceneInfoNoLableVo.setSceneType(SceneType.MEASURE);
        aiSceneInfoNoLableVo.setSceneId("-1");
        aiSceneInfoNoLableVo.setSceneName("未分类");
        aiSceneInfoNoLableVo.setDescription("未分类");
        Set<Integer> dimSetAll = new HashSet<>();
        aiSceneInfoItems.forEach(aiSceneInfoItem -> {
            if (!labelMeasureIds.contains(aiSceneInfoItem.getId())) {
                aiSceneInfoNoLableVo.getMeasureBasicInfoVOS().add(aiSceneInfoItemMap.get(aiSceneInfoItem.getId()));
                dimSetAll.addAll(measureManager.getDimensionByMeas(aiSceneInfoItem.getId()));
            }
        });
        List<Dimension> dimensions = dimensionMapper.selectBatchIds(dimSetAll);
        aiSceneInfoNoLableVo.getDimension().addAll(dimensions);
        aiSceneInfoVoList.add(aiSceneInfoNoLableVo);


        return aiSceneInfoVoList;
    }

    private List<AiSceneInfoVo> groupByLabel(List<MeasLabelGroupDTO> measLabelGroupDTOList, List<DaIndicatorLabel> daIndicatorLabels, Map<Integer, CategoryNodeItem> aiSceneInfoItemMap, Boolean isDefault) {
        List<AiSceneInfoVo> aiSceneInfoVoList = new ArrayList<>();
        Map<Long, DaIndicatorLabel> daIndicatorLabelMap = daIndicatorLabels.stream().collect(Collectors.toMap(DaIndicatorLabel::getId, Function.identity(), (ex, re) -> ex));

        Boolean isAddDefault = false;
        AiSceneInfoVo aiSceneInfoDefaultVo = new AiSceneInfoVo();

        for (MeasLabelGroupDTO groupDTO : measLabelGroupDTOList) {
            if (null != daIndicatorLabelMap.get(groupDTO.getLabelId())) {
                Set<Integer> dimSetAll = new HashSet<>();
                DaIndicatorLabel daIndicatorLabel = daIndicatorLabelMap.get(groupDTO.getLabelId());
                AiSceneInfoVo aiSceneInfoVo = new AiSceneInfoVo();
                aiSceneInfoVo.setSceneType(SceneType.MEASURE);
                aiSceneInfoVo.setSceneId(daIndicatorLabel.getId().toString());
                aiSceneInfoVo.setSceneName(daIndicatorLabel.getName());
                aiSceneInfoVo.setDescription(daIndicatorLabel.getDescription());
                Arrays.stream(groupDTO.getMeasIds().split(",")).forEach(id -> {
                    if (null != aiSceneInfoItemMap.get(Integer.parseInt(id))) {
                        aiSceneInfoVo.getMeasureBasicInfoVOS().add(aiSceneInfoItemMap.get(Integer.parseInt(id)));
                    }
                    dimSetAll.addAll(measureManager.getDimensionByMeas(Integer.parseInt(id)));

                });
                if (!dimSetAll.isEmpty()) {
                    List<Dimension> dimensionList = dimensionMapper.selectList(Wrappers.<Dimension>lambdaQuery().in(Dimension::getId, dimSetAll).eq(Dimension::getViewType, ViewType.CHARACTER.getValue()));
                    dimensionList = dimensionList.stream().filter(d -> !d.getCnName().contains("ID")).collect(Collectors.toList());
//                    List<Dimension> dimAvailable = getAvailableDimensions(dimensionList);
                    aiSceneInfoVo.getDimension().addAll(dimensionList);
                }
                if (Objects.equals(daIndicatorLabel.getName(), defaultLabelName)) {
                    isAddDefault = true;
                    BeanUtils.copyProperties(aiSceneInfoVo, aiSceneInfoDefaultVo);
                } else {
                    aiSceneInfoVoList.add(aiSceneInfoVo);
                }
            }
        }

        if (isDefault && isAddDefault && aiSceneInfoVoList.isEmpty()) {
            aiSceneInfoVoList.add(aiSceneInfoDefaultVo);
        }
        return aiSceneInfoVoList;
    }

    private List<Dimension> getAvailableDimensions(List<Dimension> dimensionList) {
        List<Dimension> natureDimList = new ArrayList<>();
        List<Dimension> noNatureDateDimList = new ArrayList<>();
        List<Dimension> haveDimList = new ArrayList<>();

        for (Dimension dimension : dimensionList) {

            if (dimension.getCnName().contains("ID")) {
                continue;
            }
            if (ViewType.isDate(dimension.getViewType())) {
                ViewType viewType = ViewType.findByInt(dimension.getViewType()).orElse(null);
                if (viewType != null) {
                    if (dimension.getCnName().contains("自然日期")) {
                        if (!Objects.equals(dimension.getViewType(), ViewType.SEASON.getValue())
                                && !Objects.equals(dimension.getViewType(), ViewType.WEEK.getValue())) {
                            dimension.setCnName(viewType.getName());
                            natureDimList.add(dimension);
                        }
                    } else {
                        dimension.setCnName(viewType.getName());
                        noNatureDateDimList.add(dimension);
                    }
                }
                continue;
            }
            haveDimList.add(dimension);
        }
        if (!natureDimList.isEmpty()) {
            haveDimList.addAll(natureDimList);
        } else if (!noNatureDateDimList.isEmpty()) {
            haveDimList.addAll(noNatureDateDimList);
        }

        return haveDimList;

    }

    @Autowired
    ITSpaceService itSpaceService;

    public Set<Integer> listAuthMeasureByCurUser() {
        MeasureQueryParam query = new MeasureQueryParam();
//        List<TSpace> tSpaceList = tSpaceMapper.selectList(Wrappers.<TSpace>lambdaQuery().orderByDesc(TSpace::getId));

        TSpace tSpace = itSpaceService.getAiSpaceById();
        query.setSpaceId(tSpace.getId());

        MetadataCache metadataCache = cacheManager.getMetadataCache();
        Map<Integer, Measure> allMeasureMap = metadataCache.getAllMeasureMap();
        Set<Integer> authMeasureIds =new HashSet<>();
        String username = UserThreadLocalUtil.getUserName();
        List<TSuperAdmin> superAdmins = superAdminService.list(Wrappers.<TSuperAdmin>lambdaQuery().eq(TSuperAdmin::getEmpCode, username));
        if (Objects.isNull(query.getSpaceId()) || !CollectionUtils.isEmpty(superAdmins)) {
            // 不传空间id或者是超级管理员，则拥有所有指标权限
            authMeasureIds = allMeasureMap.keySet();
        } else {
            UserContext userContext = userManager.getUserContext(query.getSpaceId(), username);
            if (userContext == null || userContext.getAuthMeasures().isEmpty()) {
                return new HashSet<>();
            }
            for (Measure measureItem : userContext.getAuthMeasures()) {
                if(null !=measureItem && null != measureItem.getId()){
                    authMeasureIds.add(measureItem.getId());
                }
            }

        }
        return authMeasureIds;
    }

    //todo 后续增加缓存
    public List<CategoryNodeItem> listMeasureByCurUser() {
        MetadataCache metadataCache = cacheManager.getMetadataCache();
        Map<Integer, Measure> allMeasureMap = metadataCache.getAllMeasureMap();
        Set<Integer> authMeasureIds = listAuthMeasureByCurUser();
        List<CategoryNodeItem> result = allMeasureMap.values().stream()
                .filter(measure -> bloodManager.hasFactTable(measure.getId())).map(m -> {
                    CategoryNodeItem item = new CategoryNodeItem();
                    BeanUtils.copyProperties(m, item);
                    item.setType("measure");
                    item.setHasAuth(authMeasureIds.contains(m.getId()));
                    item.setOnline(m.getOnline());
                    String offlineReason = "当前指标暂不可查，原因" + m.getOfflineOperator() + "在" + m.getOfflineTime().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")) + "将指标下线，下线原因：" + m.getOfflineRemark();
                    item.setOfflineReason(offlineReason);
                    return item;
                }).collect(Collectors.toList());
        return result;
    }

    @Autowired
    MeasureMapper measureMapper;

    @Override
    public List<CategoryNodeItem> sceneMeasDetail(AiSceneDetailVo aiSceneDetailVo) {

        List<DaMeasLabel> daMeasLabels = daMeasLabelMapper.selectList(Wrappers.<DaMeasLabel>lambdaQuery().eq(DaMeasLabel::getLabelId, Long.parseLong(aiSceneDetailVo.getSceneId())));
        List<Integer> measIds = daMeasLabels.stream().map(DaMeasLabel::getMeasId).collect(Collectors.toList());
        List<Measure> measurePage = measureMapper.selectList(Wrappers.<Measure>lambdaQuery().in(!measIds.isEmpty(), Measure::getId, measIds)
                .and(StringUtils.hasLength(aiSceneDetailVo.getKeyword()), query -> query.like(StringUtils.hasLength(aiSceneDetailVo.getKeyword()), Measure::getCnName, aiSceneDetailVo.getKeyword()))
                .orderByDesc(Measure::getUpdateTime));
        Set<Integer> authMeasureIds = listAuthMeasureByCurUser();

        Set<String> createBys = measurePage.stream().map(Measure::getCreateUser).collect(Collectors.toSet());

        Map<String, User> userMap = userManager.getUserMapByUsernames(createBys);

        List<CategoryNodeItem> result = measurePage.stream()
                .filter(measure -> bloodManager.hasFactTable(measure.getId())).map(m -> {
                    CategoryNodeItem item = new CategoryNodeItem();
                    BeanUtils.copyProperties(m, item);
                    item.setType("measure");
                    item.setHasAuth(authMeasureIds.contains(m.getId()));
                    item.setOnline(m.getOnline());
                    String offlineReason = "当前指标暂不可查，原因" + m.getOfflineOperator() + "在" + m.getOfflineTime().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")) + "将指标下线，下线原因：" + m.getOfflineRemark();
                    item.setOfflineReason(offlineReason);
                    item.setMangerInfo(userMap.get(m.getCreateUser()));
                    return item;
                }).collect(Collectors.toList());
        return result;
    }

    @Override
    public List<Dimension> sceneDimDetail(AiSceneDetailVo aiSceneDetailVo) {

        List<DaMeasLabel> daMeasLabels = daMeasLabelMapper.selectList(Wrappers.<DaMeasLabel>lambdaQuery().eq(DaMeasLabel::getLabelId, Long.parseLong(aiSceneDetailVo.getSceneId())));
        Set<Integer> measIds = daMeasLabels.stream().map(DaMeasLabel::getMeasId).collect(Collectors.toSet());
        Set<Integer> dimSetAll = new HashSet<>();
        measIds.forEach(measId -> {
            dimSetAll.addAll(measureManager.getDimensionByMeas(measId));
        });

        List<Dimension> dimensions = dimensionMapper.selectList(Wrappers.<Dimension>lambdaQuery()
                .in(!dimSetAll.isEmpty(), Dimension::getId, dimSetAll)
                .and(StringUtils.hasLength(aiSceneDetailVo.getKeyword()), query -> query.like(StringUtils.hasLength(aiSceneDetailVo.getKeyword()), Dimension::getCnName, aiSceneDetailVo.getKeyword()))
                .orderByDesc(Dimension::getUpdateTime));

        dimensions = dimensions.stream().filter(d -> !d.getCnName().contains("ID")).collect(Collectors.toList());
//        dimensions = getAvailableDimensions(dimensions);
        return dimensions;
    }

    @Override
    public PageVO<CategoryNodeItem> marketMeasDetail(AiMarkerDetailVo aiSceneDetailVo) {

        Page<Measure> measurePage = measureMapper.selectPage(new Page<>(aiSceneDetailVo.getPageNo(), aiSceneDetailVo.getPageSize()),
                Wrappers.<Measure>lambdaQuery()
                        .and(StringUtils.hasLength(aiSceneDetailVo.getKeyword()), query -> query.like(StringUtils.hasLength(aiSceneDetailVo.getKeyword()), Measure::getCnName, aiSceneDetailVo.getKeyword())
                                .orderByDesc(Measure::getUpdateTime)));
        Set<Integer> authMeasureIds = listAuthMeasureByCurUser();

        List<CategoryNodeItem> result = measurePage.getRecords().stream()
                .filter(measure -> bloodManager.hasFactTable(measure.getId())).map(m -> {
                    CategoryNodeItem item = new CategoryNodeItem();
                    BeanUtils.copyProperties(m, item);
                    item.setType("measure");
                    item.setHasAuth(authMeasureIds.contains(m.getId()));
                    item.setOnline(m.getOnline());
                    String offlineReason = "当前指标暂不可查，原因" + m.getOfflineOperator() + "在" + m.getOfflineTime().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")) + "将指标下线，下线原因：" + m.getOfflineRemark();
                    item.setOfflineReason(offlineReason);
                    return item;
                }).collect(Collectors.toList());

        PageVO<CategoryNodeItem> measurePageVO = new PageVO<>(measurePage.getTotal(), result);
        return measurePageVO;
    }

    @Autowired
    DimensionMapper dimensionMapper;

    @Override
    public PageVO<Dimension> marketDimDetail(AiMarkerDetailVo aiSceneDetailVo) {

        Page<Dimension> dimensionPage = dimensionMapper.selectPage(new Page<>(aiSceneDetailVo.getPageNo(), aiSceneDetailVo.getPageSize()),
                Wrappers.<Dimension>lambdaQuery().and(StringUtils.hasLength(aiSceneDetailVo.getKeyword()), query -> query.like(StringUtils.hasLength(aiSceneDetailVo.getKeyword()), Dimension::getCnName, aiSceneDetailVo.getKeyword())
                        .orderByDesc(Dimension::getUpdateTime)));

        PageVO<Dimension> measurePageVO = new PageVO<>(dimensionPage.getTotal(), dimensionPage.getRecords());
        return measurePageVO;
    }

}
