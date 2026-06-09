package com.graphinsight.indicator.controller.datagpt;

import com.baomidou.dynamic.datasource.toolkit.DynamicDataSourceContextHolder;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.dynamic.datasource.annotation.DS;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.graphinsight.indicator.annotation.IgnoreWebLog;
import com.graphinsight.indicator.auto.entity.*;
import com.graphinsight.indicator.auto.entity.DimAllValuesInfo;
import com.graphinsight.indicator.auto.entity.Dimension;
import com.graphinsight.indicator.auto.entity.Measure;
import com.graphinsight.indicator.auto.mapper.DaMeasLabelMapper;
import com.graphinsight.indicator.auto.mapper.DimAllValuesMapper;
import com.graphinsight.indicator.auto.service.ITSpaceService;
import com.graphinsight.indicator.auto.service.ITSuperAdminService;
import com.graphinsight.indicator.constant.IndicatorConstant;
import com.graphinsight.indicator.controller.BaseController;
import com.graphinsight.indicator.dao.SpaceDao;
import com.graphinsight.indicator.enums.JdbcDataSourceType;
import com.graphinsight.indicator.enums.ViewType;
import com.graphinsight.indicator.manager.BloodManager;
import com.graphinsight.indicator.manager.CacheManager;
import com.graphinsight.indicator.manager.CategoryManager;
import com.graphinsight.indicator.manager.UserManager;
import com.graphinsight.indicator.model.*;
import com.graphinsight.indicator.model.cache.MetadataCache;
import com.graphinsight.indicator.model.dto.UserContext;
import com.graphinsight.indicator.model.vo.*;
import com.graphinsight.indicator.service.*;
import com.graphinsight.indicator.service.wordNlp.WordDictService;
import com.graphinsight.indicator.service.wordNlp.WordSyntax;
import com.graphinsight.indicator.util.StringUtil;
import com.graphinsight.indicator.util.UserThreadLocalUtil;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiParam;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@DS("mysql")
@RestController
@Slf4j
@RequestMapping(IndicatorConstant.DATA_GPT_AI)
@Api(tags = "【dataGpt】首页接口 ")
public class AiHomeController extends BaseController {

    @Autowired
    private AiSessionService aiSessionService;

    @Autowired
    private AiSceneService aiSceneService;

    @Autowired
    private SpaceDao spaceDao;

    @Autowired
    private CacheManager cacheManager;

    @Autowired
    private BloodManager bloodManager;

    @Autowired
    private CategoryManager categoryManager;

    @Autowired
    private ITSuperAdminService superAdminService;

    @Autowired
    private UserManager userManager;

    @Autowired
    private DaMeasLabelMapper daMeasLabelMapper;


    @ApiOperation("历史会话创建")
    @PostMapping(value = "/session/create")
    public Response<AiSessionInfo> sessionCreate(@RequestBody AiSessionCreateVo aiSessionVo) {

        return Response.ok(aiSessionService.createSession(aiSessionVo));
    }


    @ApiOperation("历史会话记录列表")
    @PostMapping(value = "session/list")
    public Response<IPage<AiSessionInfo>> searchHistory(@RequestBody AiSessionVo aiSessionVo) {

        return Response.ok(aiSessionService.listSession(aiSessionVo));
    }

    @ApiOperation("历史会话详情创建")
    @PostMapping(value = "search/content/create")
    public Response searchContentCreate(@Validated @RequestBody AiContentCreateVo aiContentCreateVo) {
        aiSessionService.createContent(aiContentCreateVo);
        return Response.ok();
    }

    @ApiOperation("历史会话记录修改")
    @PostMapping(value = "session/update")
    public Response sessionUpdate(@RequestBody AiSessionUpdateVo aiSessionUpdateVo) {
        aiSessionService.updateSession(aiSessionUpdateVo);
        return Response.ok();
    }

    @ApiOperation("历史会话记录删除")
    @GetMapping(value = "session/del/{id}")
    public Response sessionDel(@PathVariable("id") Integer searchId) {
        aiSessionService.delSession(searchId);
        return Response.ok();
    }

    @ApiOperation("指定历史会话记录的详情")
    @PostMapping(value = "session/detail")
    public Response<IPage<AiSearchInfo>> sessionDetail(@RequestBody AiSessionDetailVo aiSessionDetailVo) {

        return Response.ok(aiSessionService.getSessionDetail(aiSessionDetailVo));
    }


    @ApiOperation("场景列表")
    @GetMapping(value = "/scene/list")
    public Response<List<AiSceneInfoVo>> sceneList() {
        return Response.ok(aiSceneService.sceneList());
    }

    @ApiOperation("场景指标详情列表信息")
    @PostMapping(value = "/scene/meas/detail")
    public Response<List<CategoryNodeItem>> sceneMeasDetail(@RequestBody AiSceneDetailVo aiSceneDetailVo) {

        return Response.ok(aiSceneService.sceneMeasDetail(aiSceneDetailVo));
    }

    @ApiOperation("场景维度详情列表信息")
    @PostMapping(value = "/scene/dim/detail")
    public Response<List<DimensionVO>> sceneDimDetail(@RequestBody AiSceneDetailVo aiSceneDetailVo) {

        return Response.ok(aiSceneService.sceneDimDetail(aiSceneDetailVo));
    }

    @ApiOperation("数据市场全量场景")
    @PostMapping(value = "/market/scene/list")
    public Response<Page<CategoryNodeItem>> marketSceneList(@RequestBody AiMarkerDetailVo aiSceneDetailVo) {

        return Response.ok(aiSceneService.marketSceneList(aiSceneDetailVo));
    }

    @ApiOperation("数据市场全量指标列表")
    @PostMapping(value = "/market/meas/detail")
    public Response<Page<CategoryNodeItem>> marketMeasDetail(@RequestBody AiMarkerDetailVo aiSceneDetailVo) {

        return Response.ok(aiSceneService.marketMeasDetail(aiSceneDetailVo));
    }

    @ApiOperation("数据市场全量维度列表")
    @PostMapping(value = "/market/dim/detail")
    public Response<Page<Dimension>> marketDimDetail(@RequestBody AiMarkerDetailVo aiSceneDetailVo) {

        return Response.ok(aiSceneService.marketDimDetail(aiSceneDetailVo));
    }

    @Autowired
    private DimensionQueryService dimQueryService;

    @PostMapping(value = "/dimension/value/list")
    @ResponseBody
    @IgnoreWebLog
    public Response getDimensionValues(@RequestBody DimensionQueryParam dimQueryParam) {

        Response<PageData> response = null;
        PageData pageData = null;

        try {
            DynamicDataSourceContextHolder.push(JdbcDataSourceType.MYSQL.getDesc());
            pageData = this.dimQueryService.execQueryDimensionValues(dimQueryParam, false);
            response = Response.ok("查询成功", pageData);
        } catch (Exception ex) {
            ex.printStackTrace();
            log.error("调用异常:", ex);
            response = Response.error("查询失败");
            response.setErrorStackTrace(ex.getStackTrace());
            response.setErrorMessage(ex.toString());
        }

        return response;

    }

    @Autowired
    ITSpaceService itSpaceService;

    @ApiOperation("推荐问题列表")
    @GetMapping(value = "/recommend/questions")
    public Response recommendQuestion(
            @ApiParam("标签ID")
            String labelId) {
        //推荐问题数量
        Integer recommendQuestionCount = 4;
        List<AiQuestionTemplate> questionTemplates = new ArrayList() {{
            add(new AiQuestionTemplate("不同{dimension1}的{measure1}分别是多少？", ViewType.CHARACTER.getValue()));
            add(new AiQuestionTemplate("各{dimension1}的{measure1}汇总值是多少？", ViewType.CHARACTER.getValue()));
//            add(new AiQuestionTemplate("{measure1}最高的三个{dimension1}是？", ViewType.CHARACTER.getValue()));
//            add(new AiQuestionTemplate("按{measure1}降序，各{dimension1}的排名？", -1));
//            add(new AiQuestionTemplate("近两个月，{measure1}增长最多的{dimension1}前5名？", -1));
            add(new AiQuestionTemplate("今年的总{measure1}是多少？", ViewType.YEAR.getValue()));
            add(new AiQuestionTemplate("{measure1}的月趋势", ViewType.MONTH.getValue()));
//            add(new AiQuestionTemplate("近两年的，各{dimension1}的{measure1}的年环比", ViewType.CHARACTER.getValue()));
//            add(new AiQuestionTemplate("不同{dimension1}下，各{dimension2}的{measure1}分别是多少？", -1, 1, 2));
//            add(new AiQuestionTemplate("各{dimension1}的{measure1}和{measure2}分别是多少？", -1, 2, 1));
            add(new AiQuestionTemplate("最近三个月的平均{measure1}是多少？", ViewType.MONTH.getValue()));
//            add(new AiQuestionTemplate("近三个月的{measure1}与各月环比分别是多少？", ViewType.MONTH.getValue()));
//            add(new AiQuestionTemplate("本季度的{measure1}相较上季度变化多少？", ViewType.SEASON.getValue()));
//            add(new AiQuestionTemplate("上个季度的{measure1}是多少？", ViewType.SEASON.getValue()));
//            add(new AiQuestionTemplate("本周的{measure1}相较上季度变化多少？", ViewType.SEASON.getValue()));
//            add(new AiQuestionTemplate("上周的{measure1}是多少？", ViewType.WEEK.getValue()));
//            add(new AiQuestionTemplate("本周的{measure1}是多少？", ViewType.WEEK.getValue()));
            add(new AiQuestionTemplate("昨天的{measure1}是多少？", ViewType.DAY.getValue()));
            add(new AiQuestionTemplate("今年各月的{measure1}是多少？", ViewType.MONTH.getValue()));
        }};

        //取一个空间
        //List<Space> spaces = spaceDao.findAll(PageRequest.of(0, 1, Sort.by(Sort.Direction.DESC, "id"))).getContent();
        TSpace spaces = itSpaceService.getAiSpaceById();
        Long spaceId = spaces.getId();

        MetadataCache metadataCache = cacheManager.getMetadataCache();
        Map<Integer, Measure> allMeasureMap = metadataCache.getAllMeasureMap();
        String username = UserThreadLocalUtil.getUserName();
        Set<Integer> authMeasureIds;
        List<TSuperAdmin> superAdmins = superAdminService.list(Wrappers.<TSuperAdmin>lambdaQuery().eq(TSuperAdmin::getEmpCode, username));
        if (Objects.isNull(spaceId) || !org.springframework.util.CollectionUtils.isEmpty(superAdmins)) {
            // 不传空间id或者是超级管理员，则拥有所有指标权限
            authMeasureIds = allMeasureMap.keySet();
        } else {
            UserContext userContext = userManager.getUserContext(spaceId, username);
            authMeasureIds = userContext.getAuthMeasures().stream().filter(Objects::nonNull).map(Measure::getId).collect(Collectors.toSet());
        }

        if (CollectionUtils.isEmpty(authMeasureIds)) {
            log.info("推荐问题接口:用户没有有权限的指标");
            return Response.ok(Collections.EMPTY_LIST);
        }

        List<Integer> labelMeasIdList = new ArrayList<>();

        if (StringUtil.isEmpty(labelId)) {
            log.info("推荐问题接口:标签ID为空");
            labelMeasIdList.addAll(allMeasureMap.keySet());
        } else {
            List<DaMeasLabel> daMeasLabels = daMeasLabelMapper.selectList(new QueryWrapper<DaMeasLabel>().eq("label_id", labelId).eq("is_del", 0));
            labelMeasIdList.addAll(daMeasLabels.stream().map(DaMeasLabel::getMeasId).collect(Collectors.toList()));
        }

        //所有有权限的指标
        List<Measure> hasAuthMeasures = allMeasureMap.values().stream()
                .filter(measure -> labelMeasIdList.contains(measure.getId()))
                .filter(measure -> bloodManager.hasFactTable(measure.getId()))
                .filter(measure -> authMeasureIds.contains(measure.getId()))
                .filter(measure -> measure.getOnline() > 0)
                .collect(Collectors.toList());

        Map<Integer, Dimension> allDimensionMap = metadataCache.getAllDimensionMap();

        //随机取4个  万一不够4个就有多少取多少
        Collections.shuffle(hasAuthMeasures);
        Collections.shuffle(questionTemplates);
        List<String> questions = new ArrayList<>();
        for (int i = 0; ; ++i) {
            if (i >= hasAuthMeasures.size()) {
                log.info("推荐问题接口:没有足够的指标");
                break;
            }
            if (questions.size() >= recommendQuestionCount) {
                //正常结束逻辑 不打印日志
                break;
            }
            if (i >= questionTemplates.size()) {
                log.info("推荐问题接口:没有足够的模板");
                break;
            }
            String question = generateQuestion(hasAuthMeasures.get(i), questionTemplates.get(questions.size()), allDimensionMap, allMeasureMap);
            if (question != null) {
                questions.add(question);
            }
        }

        return Response.ok(questions);
    }

    private String generateQuestion(Measure measure, AiQuestionTemplate template, Map<Integer, Dimension> allDimensionMap, Map<Integer, Measure> allMeasureMap) {
        RelatedSet relatedSet = new RelatedSet();
        List<Measure> usedMeasure = new ArrayList<>();
        usedMeasure.add(measure);
        relatedSet.setMeasureSet(new HashSet() {{
            add(measure.getId());
        }});
        RelatedSet resultRelatedSet = bloodManager.listRelatedSet(relatedSet);

        if (template.getMeasureCount() > 1) {
            //最多支持2个 所以不做循环处理了
            Set<Integer> measureSet = resultRelatedSet.getMeasureSet();
            List<Integer> filteredMeasures = measureSet.stream().filter(a -> !a.equals(measure.getId())).collect(Collectors.toList());
            if (CollectionUtils.isEmpty(filteredMeasures) || !allMeasureMap.containsKey(filteredMeasures.get(0))) {
                return null;
            }
            Collections.shuffle(filteredMeasures);
            relatedSet.getMeasureSet().add(filteredMeasures.get(0));
            resultRelatedSet = bloodManager.listRelatedSet(relatedSet);
            usedMeasure.add(allMeasureMap.get(filteredMeasures.get(0)));
        }

        Set<Integer> dimensionSet = resultRelatedSet.getDimensionSet();
        if (CollectionUtils.isEmpty(dimensionSet)) {
            return null;
        }
        List<Dimension> relatedDimensions = allDimensionMap.values().stream().filter(a -> dimensionSet.contains(a.getId()) && !a.getCnName().contains("ID")).collect(Collectors.toList());
        if (template.getViewType() != -1) {
            relatedDimensions = relatedDimensions.stream().filter(a -> template.getViewType().equals(a.getViewType())).collect(Collectors.toList());
        }

        if (CollectionUtils.isEmpty(relatedDimensions)) {
            return null;
        }
        Collections.shuffle(relatedDimensions);
        String question = template.getTemplate();
        for (int i = 1; i <= template.getMeasureCount(); ++i) {
            question = question.replaceAll("\\{measure" + i + "}", usedMeasure.get(i - 1).getCnName().contains("_") ? usedMeasure.get(i - 1).getCnName().split("_")[0] : usedMeasure.get(i - 1).getCnName());
        }
        for (int i = 1; i <= template.getDimensionCount(); ++i) {
            String dimensionName = relatedDimensions.get(i - 1).getCnName();
            if(Objects.equals(dimensionName, "自然日期_Q") || Objects.equals(dimensionName, "自然日期_W")){
                continue;
            }
            dimensionName = dimensionName
                    .replaceAll("自然日期_D", "日")
                    .replaceAll("自然日期_W", "周")
                    .replaceAll("自然日期_M", "月")
                    .replaceAll("自然日期_Q", "季")
                    .replaceAll("自然日期_Y", "年");

            question = question.replaceAll("\\{dimension" + i + "}", dimensionName.contains("_") ? dimensionName.split("_")[0] : dimensionName);
        }
        return question;
    }


    private static Cache<Object, Object> cache = CacheBuilder.newBuilder()
            .initialCapacity(10000)
            .concurrencyLevel(20)
            .expireAfterAccess(10, TimeUnit.MINUTES)
            .build();

    @Autowired
    DimAllValuesMapper dimAllValuesMapper;

    @PostMapping("/dim/all/values/add")
    @ResponseBody
    @IgnoreWebLog
    public Response getDimensionValuesAdd(@RequestBody DimensionQueryParam dimQueryParam) {

        Response<PageData> response = null;
        PageData pageData = null;

        try {
            DynamicDataSourceContextHolder.push(JdbcDataSourceType.MYSQL.getDesc());

            String key = dimQueryParam.getkey();
            Object obj = cache.getIfPresent(key);
            if (null == obj) {
                pageData = this.dimQueryService.execQueryDimensionValues(dimQueryParam, false);
                cache.put(key, pageData);

            } else {
                pageData = (PageData) obj;
            }

            Map<String, Dimension> dimensionMap = cacheManager.getMetadataCache().getAllDimensionCodeMap();
            List<DimAllValuesInfo> dimAllValuesInfoList = new ArrayList<>();

            int i = 0;
            for (List<Cell> cells : pageData.getCellList()) {

                if (!cells.isEmpty()) {

                    DimAllValuesInfo dimAllValuesInfo = new DimAllValuesInfo();
                    Cell cellItem = cells.get(0);
                    if(null != dimensionMap.get(cellItem.getCode())){
                        dimAllValuesInfo.setDimCode(cellItem.getCode());
                        dimAllValuesInfo.setDimId(dimensionMap.get(cellItem.getCode()).getId());
                        dimAllValuesInfo.setDimName(cellItem.getName());
                        dimAllValuesInfo.setValueKey(cellItem.getId());
                        dimAllValuesInfo.setValueText(cellItem.getData());
                        dimAllValuesInfo.setValueFormatText(cellItem.getData().toLowerCase());
                        dimAllValuesInfoList.add(dimAllValuesInfo);
                        i++;
                    }

                    if (i % 1000 == 0) {
                        dimAllValuesMapper.insertBatch(dimAllValuesInfoList);
                        dimAllValuesInfoList = new ArrayList<>();
                    }

                }

            }

            if (!CollectionUtils.isEmpty(dimAllValuesInfoList) && dimAllValuesInfoList.size() > 0) {
                dimAllValuesMapper.insertBatch(dimAllValuesInfoList);
            }

//            pageData.getCellList().forEach(cell -> {
//
//                int i = 0;
//                if (!cell.isEmpty()) {
//                    DimAllValuesInfo dimAllValuesInfo = new DimAllValuesInfo();
//                    Cell cellItem = cell.get(0);
//                    if(null != dimensionMap.get(cellItem.getCode())){
//                        dimAllValuesInfo.setDimCode(cellItem.getCode());
//                        dimAllValuesInfo.setDimId(dimensionMap.get(cellItem.getCode()).getId());
//                        dimAllValuesInfo.setDimName(cellItem.getName());
//                        dimAllValuesInfo.setValueKey(cellItem.getId());
//                        dimAllValuesInfo.setValueText(cellItem.getData());
//                        dimAllValuesInfo.setValueFormatText(cellItem.getData().toLowerCase());
//                        dimAllValuesInfoList.add(dimAllValuesInfo);
//                        i++;
//                    }
//
//                }
//
//                if (i % 1000 == 0) {
//
//                }
//
//
//            });

            response = Response.ok("查询成功", pageData);
        } catch (Exception ex) {
            ex.printStackTrace();
            log.error("调用异常:", ex);
            response = Response.error("查询失败");
            response.setErrorStackTrace(ex.getStackTrace());
            response.setErrorMessage(ex.toString());
        }

        return response;

    }


    @Autowired
    WordDictService wordDictService;
    @Autowired
    KeyWord2Service keyWord2Service;
    @PostMapping("/split/word/reload")
    @GetMapping
    @IgnoreWebLog
    public Response reloadSplitWord() {
//        keyWord2Service.reloadSplit();
        wordDictService.init();
        return Response.ok();
    }

}
