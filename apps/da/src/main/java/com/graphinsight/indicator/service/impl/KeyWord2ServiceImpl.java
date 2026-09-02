package com.graphinsight.indicator.service.impl;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.ibm.icu.text.RuleBasedNumberFormat;
import com.graphinsight.indicator.auto.entity.*;
import com.baomidou.dynamic.datasource.annotation.DS;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.graphinsight.indicator.auto.entity.DimAllValuesInfo;
import com.graphinsight.indicator.auto.entity.WordValues;
import com.graphinsight.indicator.auto.mapper.*;
import com.graphinsight.indicator.auto.service.ITSpaceService;
import com.graphinsight.indicator.auto.service.ITSuperAdminService;
import com.graphinsight.indicator.constant.CommonConstants;
import com.graphinsight.indicator.controller.DimMeasRelationController;
import com.graphinsight.indicator.doris.entity.Columns;
import com.graphinsight.indicator.doris.mapper.ColumnsMapper;
import com.graphinsight.indicator.auto.entity.AiQuestionInfo;
import com.graphinsight.indicator.enums.*;
import com.graphinsight.indicator.manager.BloodManager;
import com.graphinsight.indicator.manager.CacheManager;
import com.graphinsight.indicator.manager.UserManager;
import com.graphinsight.indicator.auto.mapper.AiQuestionInfoMapper;
import com.graphinsight.indicator.model.*;
import com.graphinsight.indicator.model.BaseConfigure;
import com.graphinsight.indicator.model.DataSource;
import com.graphinsight.indicator.model.Dimension;
import com.graphinsight.indicator.model.Measure;
import com.graphinsight.indicator.model.cache.DimensionCache;
import com.graphinsight.indicator.model.cache.DwTableCache;
import com.graphinsight.indicator.model.cache.MeasureCache;
import com.graphinsight.indicator.model.cache.MetadataCache;
import com.graphinsight.indicator.model.dto.BaseInfoDTO;
import com.graphinsight.indicator.model.dto.UserContext;
import com.graphinsight.indicator.model.vo.*;
import com.graphinsight.indicator.service.*;
import com.graphinsight.indicator.service.gpt4.LiCloudGptClient;
import com.graphinsight.indicator.service.wordNlp.WordSyntax;
import com.graphinsight.indicator.util.StringUtil;
import com.graphinsight.indicator.util.UserThreadLocalUtil;
import com.xkzhangsan.time.nlp.TimeNLP;
import com.xkzhangsan.time.nlp.TimeNLPUtil;
import lombok.extern.slf4j.Slf4j;
import org.ansj.domain.Result;
import org.ansj.domain.Term;
import org.ansj.splitWord.analysis.DicAnalysis;
import org.apache.commons.collections.map.HashedMap;
import org.codehaus.jackson.map.Serializers;
import org.nlpcn.commons.lang.tire.domain.Forest;
import org.nlpcn.commons.lang.tire.domain.Value;
import org.nlpcn.commons.lang.tire.library.Library;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;

import javax.annotation.Resource;
import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalAdjusters;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Service
@DS("mysql")
@Slf4j
public class KeyWord2ServiceImpl implements KeyWord2Service {

    @Autowired
    private IndicatorService indicatorService;

    @PersistenceContext
    private EntityManager entityManager;

    @Autowired
    private ChartQueryService chartQueryService;

    @Resource
    CacheManager cacheManager;
    @Autowired
    private BloodManager bloodManager;
    @Autowired
    private WordValuesMapper wordValuesMapper;

    @Autowired
    private WordInfosMapper wordInfosMapper;
    @Autowired
    private DimMeasRelationController dimMeasRelationController;

    @Autowired
    MeasureRelateRecodeMapper measureRelateRecodeMapper;

    @Autowired
    AiQuestionInfoMapper aiQuestionInfoMapper;
    @Autowired
    AiSearchInfoMapper aiSearchInfoMapper;

    @Autowired
    AiUserCollectMapper aiUserCollectMapper;
    @Autowired
    AiAnalysisContextMapper aiAnalysisContextMapper;

    @Autowired
    ITSuperAdminService superAdminService;
    @Autowired
    private UserManager userManager;

    @Autowired
    AiShowIndicatorMapper aiShowIndicatorMapper;


    public static final List<String> dateDefaultList = new ArrayList<String>(Arrays.asList("DIM_4e41a99d4b964cc0a66dd7c02356c473", "DIM_0a61b0022ae241e7a400399e97dc1e63", "DIM_a15f9bcd0235428fbaf164b584f8055f"));

    public static final List<String> eqWordList = new ArrayList<String>(Arrays.asList("为", "是", "等于"));


    public static final List<String> rateWordList = new ArrayList<String>(Arrays.asList("日同比", "月同比", "年同比", "日环比", "月环比", "年环比"));

    @Override
    public List<AiSplitTextVo.Tokens> getSplitWordInfo(AiSplitTextVo aiSplitTextVo) {


        String info = "";

        String needSplitInfo = "";

        List<AiSplitTextVo.Tokens> listTokens = new ArrayList<>();

        for (int i = 0; i < aiSplitTextVo.getTokens().size(); i++) {


            Object obj = aiSplitTextVo.getTokens().get(i);
            if (obj instanceof String) {
                if (obj == "") {
                    continue;
                }
                needSplitInfo += obj;

            } else if (obj instanceof AiSplitTextVo.Tokens) {
                AiSplitTextVo.Tokens itemToken = (AiSplitTextVo.Tokens) obj;
                needSplitInfo += itemToken.getWord();
//                if (Objects.equals(itemToken.getShowType(), "unknow")) {
//                    needSplitInfo += itemToken.getWord();
//                } else if (Objects.equals(itemToken.getShowType(), "measure")) {
//                    listTokens.add(i, itemToken);
//                } else if (Objects.equals(itemToken.getShowType(), "dim")) {
//                    listTokens.add(i, itemToken);
//                }
            } else {
                String jsonObj = com.alibaba.fastjson.JSON.toJSONString(obj);
                AiSplitTextVo.Tokens itemToken = com.alibaba.fastjson.JSON.parseObject(jsonObj, AiSplitTextVo.Tokens.class);
                needSplitInfo += itemToken.getWord();
            }
        }


        List<Measure> measureList = this.indicatorService.listAllMeasure();
        Map<String, Measure> measureMap = measureList.stream().collect(Collectors.toMap(measure -> measure.getName().toLowerCase(), Function.identity(), (re, ex) -> re));


        List<Dimension> dimensionList = this.indicatorService.listAllDimension();
        List<String> dimNameList = dimensionList.stream().map(Dimension::getName).collect(Collectors.toList());


        List<String> timeSplitList = getTimeSplit(needSplitInfo);
        //List<String> timeSplitList = new ArrayList<>();
        List<Term> textList = splitInfo(needSplitInfo, measureList, dimensionList, timeSplitList);


        Map<Integer, AiSplitTextVo.Tokens> noSortInfo = new HashedMap();

        String unknowStr = "";
        for (int j = 0; j < textList.size(); j++) {
            Term term = textList.get(j);
            AiSplitTextVo.Tokens tokens = new AiSplitTextVo.Tokens();

            if (null != measureMap.get(term.getName())) {
                if (!unknowStr.equals("")) {
                    AiSplitTextVo.Tokens tokens2 = new AiSplitTextVo.Tokens();
                    tokens2.setWord(unknowStr);
                    tokens2.setShowType("unknow");
                    listTokens.add(tokens2);
                }
                unknowStr = "";
                tokens.setWord(measureMap.get(term.getName()).getName());
                tokens.setShowType("measure");
                listTokens.add(tokens);

            } else if (dimNameList.contains(term.getName())) {
                if (!unknowStr.equals("")) {
                    AiSplitTextVo.Tokens tokens2 = new AiSplitTextVo.Tokens();
                    tokens2.setWord(unknowStr);
                    tokens2.setShowType("unknow");
                    listTokens.add(tokens2);
                }
                unknowStr = "";
                tokens.setWord(term.getName());
                tokens.setShowType("dim");
                listTokens.add(tokens);
            } else if (timeSplitList.contains(term.getName())) {
                if (!unknowStr.equals("")) {
                    AiSplitTextVo.Tokens tokens2 = new AiSplitTextVo.Tokens();
                    tokens2.setWord(unknowStr);
                    tokens2.setShowType("unknow");
                    listTokens.add(tokens2);
                }
                unknowStr = "";
                tokens.setWord(term.getName());
                tokens.setShowType("date");
                listTokens.add(tokens);
            } else {
                unknowStr += term.getName();
            }

        }
        if (!unknowStr.equals("")) {
            AiSplitTextVo.Tokens tokens3 = new AiSplitTextVo.Tokens();
            tokens3.setWord(unknowStr);
            tokens3.setShowType("unknow");
            listTokens.add(tokens3);
        }

        // 最后处理顺序


        return listTokens;
    }

    public PageData getRelateMeasure(DataQueryVO dataQueryVO, String originMeasure, Boolean isData) {

        RelatedCodeSet relatedCodeSet = new RelatedCodeSet();
        relatedCodeSet.getMeasureSet().add(originMeasure);
        // 以时间维度计算，后续增加其它维度分析
        relatedCodeSet.getDimensionSet().add("DIM_4e41a99d4b964cc0a66dd7c02356c473");
        RelatedCodeSet relateCheckTemp = listRelatedSetDemo(relatedCodeSet);

        // relateCheckTemp.getMeasureSet()

        List<MeasureRelateRecode> relateRecodeList = measureRelateRecodeMapper.relateRecodeInfo(originMeasure, relateCheckTemp.getMeasureSet());
        // 获取正向相关最高的10个指标

//        relateRecodeList.stream().


        return null;
    }

    public RelatedCodeSet listRelatedSetDemo(RelatedCodeSet relatedCodeSet) {
        try {
            RelatedSet relatedSet = new RelatedSet();
            MetadataCache metadataCache = cacheManager.getMetadataCache();
            Map<Integer, com.graphinsight.indicator.auto.entity.Measure> allMeasureMap = metadataCache.getAllMeasureMap();
            Map<Integer, com.graphinsight.indicator.auto.entity.Dimension> allDimensionMap = metadataCache.getAllDimensionMap();
            Map<String, List<com.graphinsight.indicator.auto.entity.Dimension>> dimensionMap = allDimensionMap.values().stream().collect(Collectors.groupingBy(com.graphinsight.indicator.auto.entity.Dimension::getCode));
            Map<String, List<com.graphinsight.indicator.auto.entity.Measure>> measureMap = allMeasureMap.values().stream().collect(Collectors.groupingBy(com.graphinsight.indicator.auto.entity.Measure::getCode));
            Set<String> dimensionSet = relatedCodeSet.getDimensionSet();
            Set<String> measureSet = relatedCodeSet.getMeasureSet();
            Set<Integer> dimIds = dimensionSet.stream().map(code -> dimensionMap.get(code).get(0).getId()).collect(Collectors.toSet());
            Set<Integer> measIds = measureSet.stream().map(code -> measureMap.get(code).get(0).getId()).collect(Collectors.toSet());

            relatedSet.setMeasureSet(measIds);
            relatedSet.setDimensionSet(dimIds);
            relatedSet.setFilterWithRelyDimensions(relatedCodeSet.isFilterWithRelyDimensions());
            RelatedSet resultRelatedSet = bloodManager.listRelatedSet(relatedSet);

            Set<Integer> dimensionResSet = resultRelatedSet.getDimensionSet();
            Set<Integer> measureResSet = resultRelatedSet.getMeasureSet();
            Set<String> dimCodes = dimensionResSet.stream().map(id -> allDimensionMap.get(id)).filter(d -> d != null).map(d -> d.getCode()).collect(Collectors.toSet());
            Set<String> measCodes = measureResSet.stream().map(id -> allMeasureMap.get(id)).filter(m -> m != null).map(m -> m.getCode()).collect(Collectors.toSet());

            RelatedCodeSet result = new RelatedCodeSet();
            result.setMeasureSet(measCodes);
            result.setDimensionSet(dimCodes);
            result.setFilterWithRelyDimensions(relatedSet.isFilterWithRelyDimensions());
            return result;
        } catch (Exception e) {
            log.info("error infos is {}", e);
            return null;
        }

    }


    AiSearchInfo recordInfo(String word, User user, Integer queryType, Integer sessionId, PageData pageData) {
        AiSearchInfo aiSearchInfo = new AiSearchInfo();
        aiSearchInfo.setIsDel(0);
        aiSearchInfo.setAnalysisType(queryType);
        aiSearchInfo.setContent(word);
        aiSearchInfo.setContentCode(StringUtil.generateUUIDFromString(word).toString().replaceAll("-", ""));
        aiSearchInfo.setUserId(user.getId().toString());
        aiSearchInfo.setUser(user.getUsername());
        aiSearchInfo.setSessionId(sessionId);
        aiSearchInfo.setRoleType("user");
        aiSearchInfoMapper.insert(aiSearchInfo);

        AiSearchInfo aiSearchInfoSystem = new AiSearchInfo();
        aiSearchInfoSystem.setIsDel(0);
        aiSearchInfoSystem.setAnalysisType(queryType);
        aiSearchInfoSystem.setContent(com.alibaba.fastjson.JSON.toJSONString(pageData));
        aiSearchInfoSystem.setContentCode(StringUtil.generateUUIDFromString(word + "system").toString().replaceAll("-", ""));
        aiSearchInfoSystem.setUserId(user.getId().toString());
        aiSearchInfoSystem.setUser(user.getUsername());
        aiSearchInfoSystem.setSessionId(sessionId);
        aiSearchInfoSystem.setRoleType("system");
        aiSearchInfoMapper.insert(aiSearchInfoSystem);

//        pageData.setReviewSql(reviewSql);
        return aiSearchInfo;
    }

    @Autowired
    RedisCacheService redisCacheService;

    private String cache_pre = "dataCache_";

    private String cache_pre_range = "dataRange_";

    private String cache_pre_range_admin = "dataAdmin_";

    @Override
    public PageData doAction(DataQueryVO dataQueryVO) {
        return doSplitAction(dataQueryVO, dataQueryVO.getWord(), dataQueryVO.getIsData());
    }

    @Override
    public void reloadSplit() {
        init();
        return;
    }

    @Override
    public PageData doAction2(DataQueryVO dataQueryVO, String word, Boolean isData) {

        return doSplitAction(dataQueryVO, word, isData);

    }

    @Autowired
    AiMeasTemplateMapper aiMeasTemplateMapper;
    @Autowired
    WordSyntax wordSyntax;
    @Autowired
    TSpaceMapper tSpaceMapper;

    @Autowired
    MeasureMapper measureMapper;

    @Autowired
    ITSpaceService itSpaceService;


    public PageData queryNlp(DataSource dataSource) {
        //User user = UserThreadLocalUtil.get();

        String userName = dataSource.getUsername();
        if (StringUtil.isEmpty(userName)) {
            userName = UserThreadLocalUtil.getUserName();
            dataSource.setUsername(userName);
        }

        TSpace tSpace = itSpaceService.getAiSpaceById();
        dataSource.setSpaceId(tSpace.getId());

        PageData pageData = new PageData();
        Boolean isDataRange = checkDataRange(dataSource, userName);
        if (!isDataRange) {
            pageData.setDataRange(isDataRange);
            pageData.setDataAllRange(isDataRange);
            Set<String> tmpName = new HashSet<>();
            Set<String> codeSet = new HashSet<>();
            for (BaseConfigure conf : dataSource.getConfigureList()) {
                if (conf.getCode().contains("MEAS")) {
                    codeSet.add(conf.getCode());
                    tmpName.add(conf.getName());
                }
            }

            Set<String> createBys = new HashSet<>();
            createBys.add("lipengkai");
            List<User> userList = userManager.listUserByUsernames(createBys);
            // 获取管家信息
            String tip = "您尚未拥有当前指标（" + String.join("、", tmpName) + "）的权限，如需开通，请联系";
            Map<String, Object> rangeInfo = new HashMap<>();
            rangeInfo.put("tip", tip);
            rangeInfo.put("users", userList);
            pageData.setRangeInfo(rangeInfo);
            pageData.setRecordSuccess(false);
            return pageData;
        }

        if (dataSource.isData()) {
            Boolean flagIsLimit = false;
            Map<String, Object> baseInfoMap = new HashMap<>();
            dataSource.setSpaceId(tSpace.getId());
            if (null != dataSource.getLimitNum()) {
                dataSource.setPageSize(dataSource.getLimitNum());
            } else {
                dataSource.setPageSize(CommonConstants.DETAIL_DEFAULT_COUNT);
            }

            // 根据dataSource构建缓存
            Boolean isExecQuery = false;
            String cacheDataSourceKey = buildMd5Key(dataSource, "DATA_GPT_");

            dataSource.setChartShow(true);
            if (redisCacheService.hasKey(cacheDataSourceKey) && dataSource.getUseCache()) {
                pageData = redisCacheService.get(cacheDataSourceKey, PageData.class);
            } else {
                pageData = chartQueryService.execQuery(dataSource);
                // todo 简单的缓存处理 每天凌晨失效
                isExecQuery = true;
            }
            dataSource.setDataCache(!isExecQuery);

            //pageData = chartQueryService.execQuery(dataSource);


            MetadataCache metadataCache = cacheManager.getMetadataCache();
            Map<String, com.graphinsight.indicator.auto.entity.Measure> allMeasureCodeMap = metadataCache.getAllMeasureCodeMap();
            Map<String, com.graphinsight.indicator.auto.entity.Dimension> allDimensionCodeMap = metadataCache.getAllDimensionCodeMap();


            List<String> measureList = new ArrayList<>();
            List<String> dimInfoList = new ArrayList<>();

            List<Long> measureIdList = new ArrayList<>();

            dataSource.getConfigureList().forEach(confV -> {
                if (confV.getCode().contains("MEAS")) {
                    measureList.add(confV.getCode());
                    measureIdList.add(confV.getId());
                    if (null != allMeasureCodeMap.get(confV.getCode())) {
                        baseInfoMap.put(confV.getCode(), allMeasureCodeMap.get(confV.getCode()));
                    } else {
                        if (confV.getCode().contains("MEAS_LDX_")) {
                            baseInfoMap.put(confV.getCode(), confV);
                        }
                    }
                } else {
                    dimInfoList.add(confV.getCode());
                    if (null != allDimensionCodeMap.get(confV.getCode())) {
                        baseInfoMap.put(confV.getCode(), allDimensionCodeMap.get(confV.getCode()));
                    }
                }
            });

            dataSource.getFilterList().forEach(filterV -> {
                if (filterV.getCode().contains("MEAS")) {
                    if (null != allMeasureCodeMap.get(filterV.getCode())) {
                        baseInfoMap.put(filterV.getCode(), allMeasureCodeMap.get(filterV.getCode()));
                    }
                } else {
                    if (null != allDimensionCodeMap.get(filterV.getCode())) {
                        baseInfoMap.put(filterV.getCode(), allDimensionCodeMap.get(filterV.getCode()));
                    }
                }
            });

            Integer queryType = 0;
            if (dimInfoList.size() > 0) {
                queryType = 3;
            } else {
                if (measureList.size() == 1) {
                    queryType = 2;
                } else if (measureList.size() > 1) {
                    queryType = 4;
                }
            }

            if (measureList.size() > 1) {
                queryType = 4;
            }

            if (flagIsLimit) {
                queryType = 5;
            }
//            if (word.contains("占比")) {
//                queryType = 6;
//            }
            dataSource.setQueryType(queryType);
            // 记录数据到相应表

            this.setDimConfList(dataSource);

            pageData.setBaseInfoMap(baseInfoMap);
            pageData.setDataSource(dataSource);
            pageData.setSpaceId(Arrays.asList(itSpaceService.getAiSpaceById().getId()));
            // todo 目前写死根据指标获取解读
            // new QueryWrapper<AiMeasTemplate>().lambda().in(AiMeasTemplate::getMeasId, measureIdList)
            List<AiMeasTemplate> aiMeasTemplates = aiMeasTemplateMapper.selectList(null);
            if (!aiMeasTemplates.isEmpty()) {
                pageData.setExplainTemplate(aiMeasTemplates.get(0).getContent());
            }
            // 缓存记录
            // todo 简单的缓存处理 每天凌晨失效
            if (isExecQuery && !pageData.getCellList().isEmpty() && pageData.getDataAllRange()) {
                redisCacheService.put(cacheDataSourceKey, pageData, getRemainSecondsOneDay(new Date()));
                UserThreadLocalUtil.printCost("pageData");
            }
            return pageData;
        } else {
            pageData.setDataSource(dataSource);
            return pageData;
        }

    }

    private void setDimConfList(DataSource dataSource) {

        List<BaseConfigure> dimConfList = dataSource.getDimConfList();

        List<BaseConfigure> configureList = dataSource.getConfigureList();
        if (!CollectionUtils.isEmpty(configureList)) {
            for (BaseConfigure baseConfigure : configureList) {
                if (ChartQueryServiceImpl.isDimension(baseConfigure) && !this.hasDim(dimConfList, baseConfigure.getCode())) {
                    dimConfList.add(baseConfigure);
                }
            }
        }
    }

    private boolean hasDim(List<BaseConfigure> dimList, String code) {
        boolean has = false;
        if (!CollectionUtils.isEmpty(dimList) && null != code) {
            for (BaseConfigure baseConfigure : dimList) {
                if (code.equals(baseConfigure.getCode())) {
                    has = true;
                    break;
                }
            }
        }
        return has;
    }

    @Autowired
    @Qualifier("secondJdbcTemplate")
    private JdbcTemplate defaultJdbcTemplate;

    @Autowired
    TextToSqlService textToSqlService;
    @Autowired
    LiCloudGptClient liCloudGptClient;

    @Override
    public PageData queryNlpDetail(DataSource dataSource) {
        PageData pageData = new PageData();
        if (!dataSource.isData()) {
            pageData.setDataSource(dataSource);
            return pageData;
        }
        //pageData = queryDetail(dataSource);
        String toSqlWordFilter = "";
        String toSqlWordGroup = "";
        Set<Integer> dimIdList = new HashSet<>();
        for (int i = dataSource.getConfigureList().size() - 1; i >= 0; i--) {
            BaseConfigure configure = dataSource.getConfigureList().get(i);

            if (configure.getCode().contains("MEAS")) {
                dataSource.getConfigureList().remove(i);
                continue;
            }
            dimIdList.add(Math.toIntExact(configure.getId()));
            toSqlWordGroup += configure.getName() + ",";
        }
        if (!toSqlWordGroup.equals("")) {
            toSqlWordGroup = "按照" + toSqlWordGroup + "分组查看";
        }
        try {
            for (Filter filter : dataSource.getFilterList()) {
                dimIdList.add(Math.toIntExact(filter.getId()));
                if (!ViewType.isDate(filter.getViewType().getValue())) {
                    if (filter.getOperatorList().get(0).getDataList().size() > 1) {
                        toSqlWordFilter += filter.getName() + "包含" + String.join(",", filter.getOperatorList().get(0).getDataList()) + ",";
                    } else {
                        toSqlWordFilter += filter.getName() + "等于" + filter.getOperatorList().get(0).getDataList().get(0) + ",";
                    }
                } else {
                    SimpleDateFormat formatter = null;
                    SimpleDateFormat parseFormat = null;
                    Integer formatDateLeng = filter.getOperatorList().get(0).getDataList().get(0).length();
                    if (formatDateLeng == 4) {
                        // 年
                        parseFormat = new SimpleDateFormat("yyyy");
                        formatter = new SimpleDateFormat("yyyy年");

                    } else if (formatDateLeng == 6) {
                        // 月
                        parseFormat = new SimpleDateFormat("yyyyMM");
                        formatter = new SimpleDateFormat("yyyy年MM月");

                    } else {
                        // 日
                        parseFormat = new SimpleDateFormat("yyyy-MM-dd");
                        formatter = new SimpleDateFormat("yyyy年MM月dd日");

                    }
                    if (Objects.equals(filter.getOperatorList().get(0).getDataList().get(0), filter.getOperatorList().get(0).getDataList().get(1))) {

                        Date dateStart = parseFormat.parse(filter.getOperatorList().get(0).getDataList().get(0));
                        toSqlWordFilter += "日期是" + formatter.format(dateStart) + ",";
                    } else {
                        Date dateStart = parseFormat.parse(filter.getOperatorList().get(0).getDataList().get(0));
                        Date dateEnd = parseFormat.parse(filter.getOperatorList().get(0).getDataList().get(1));
                        toSqlWordFilter += "日期在" + formatter.format(dateStart) + "," + formatter.format(dateEnd) + "之间,";
                    }

                }

            }
        } catch (Exception e) {

        }


        String toSqlText = toSqlWordFilter + toSqlWordGroup;
        // 多个维度id，找到最可能得事实表，出现次数最多的，作为事实表
        Set<Integer> tableId = new HashSet<>();
        for (Integer dimId : dimIdList) {
            DimensionCache dimensionCache = cacheManager.getDimensionCache(dimId);
            Set<Integer> relatedDwTableIds = dimensionCache.getRelatedDwTableIds();
            if (tableId.isEmpty()) {
                tableId.addAll(relatedDwTableIds);
            } else {
                tableId.retainAll(relatedDwTableIds);
            }
        }


        DwTable dwTableInfo = null;
        List<String> factList = new ArrayList<>();
        String tableDDL = "SHOW CREATE TABLE ";
        for (Integer relatedDwTableId : tableId) {
            dwTableInfo = cacheManager.getMetadataCache().getDwTableMap().get(relatedDwTableId);
            tableDDL += dwTableInfo.getSchemaName() + "." + dwTableInfo.getTableDetailName();
            dataSource.setTableId(relatedDwTableId);
            break;
        }
        if (dataSource.getTableId() == 0) {
            dataSource.setSingle(false);
            pageData.setBaseInfoMap(new HashMap<>());
            buildBaseMap(dataSource, pageData);
            dataSource.setRouteType("toSql");
            pageData.setDataSource(dataSource);
            return pageData;
        }


        Map<String, Object> queryForRowSe = defaultJdbcTemplate.queryForMap(tableDDL);
        String tableDDLRes = null;
        if (null != queryForRowSe.get("Create Table")) {
            tableDDLRes = queryForRowSe.get("Create Table").toString();
        } else if (null != queryForRowSe.get("Create View")) {
            tableDDLRes = queryForRowSe.get("Create View").toString();
            tableDDLRes = tableDDLRes.replace("CREATE VIEW", "CREATE TABLE");

            int index = tableDDLRes.indexOf(" AS SELECT");
            if (index != -1) {
                tableDDLRes = tableDDLRes.substring(0, index);
            }

        }
        if (tableDDLRes != null) {
            tableDDLRes = tableDDLRes.replaceAll("\n", "").replaceAll("\r", "").replaceAll("\\) ENGINE=OLAP.*", ")");
            log.info("tableDDLRes:{}", tableDDLRes);

            String executeSql = textToSqlService.textToSql(toSqlText, tableDDLRes);

            String executeSql2 = liCloudGptClient.textToSql(toSqlText, tableDDLRes);
            if (null == executeSql) {
                dataSource.setSingle(false);
                pageData.setBaseInfoMap(new HashMap<>());
                buildBaseMap(dataSource, pageData);
                dataSource.setRouteType("toSql");
                pageData.setDataSource(dataSource);
                return pageData;
            }
            executeSql = executeSql.replaceAll("`", "").replaceAll("\n", " ").replaceAll("\r", " ").replace(dwTableInfo.getTableDetailName(), dwTableInfo.getSchemaName() + "." + dwTableInfo.getTableDetailName());
            log.info("LLM SQL:{}", executeSql);

            try {
                List<Map<String, Object>> list = defaultJdbcTemplate.queryForList(executeSql);
                pageData.setBaseInfoMap(new HashMap<>());
                pageData = buildPageData(list, factList, dataSource, executeSql);
                pageData.setReviewSql(executeSql);
                log.info("LLM SQL RES:{}", list);
            } catch (Exception e) {
                log.info("LLM SQL exec error:{}", e.getMessage(), e);
            }
        }

        buildBaseMap(dataSource, pageData);
        List<AiMeasTemplate> aiMeasTemplates = aiMeasTemplateMapper.selectList(null);
        if (!aiMeasTemplates.isEmpty()) {
            pageData.setExplainTemplate(aiMeasTemplates.get(0).getContent());
        }

        dataSource.setSingle(false);
        dataSource.setRouteType("toSql");
        pageData.setDataSource(dataSource);
        return pageData;
    }

    @Override
    public void buildBaseMap(DataSource dataSource, PageData pageData) {
        MetadataCache metadataCache = cacheManager.getMetadataCache();
        Map<String, com.graphinsight.indicator.auto.entity.Measure> allMeasureCodeMap = metadataCache.getAllMeasureCodeMap();
        Map<String, com.graphinsight.indicator.auto.entity.Dimension> allDimensionCodeMap = metadataCache.getAllDimensionCodeMap();

        for (Map.Entry<String, BaseConfigure> entry : dataSource.getConfigureMap().entrySet()) {
            if (entry.getKey().contains("MEAS_TOSQL") || entry.getKey().contains("DIM_FAKE_")) {
                dataSource.getConfigureList().add(entry.getValue());
            }
        }

        for (BaseConfigure confV : dataSource.getConfigureList()) {
            if (confV.getCode().contains("MEAS")) {
                if (null != allMeasureCodeMap.get(confV.getCode())) {
                    pageData.getBaseInfoMap().put(confV.getCode(), allMeasureCodeMap.get(confV.getCode()));
                    dataSource.getBaseInfoMap().put(confV.getCode(), allMeasureCodeMap.get(confV.getCode()));
                } else {
                    if (confV.getCode().contains("MEAS_LDX_")) {
                        pageData.getBaseInfoMap().put(confV.getCode(), confV);
                    }
                }
            } else {
                if (null != allDimensionCodeMap.get(confV.getCode())) {
                    pageData.getBaseInfoMap().put(confV.getCode(), allDimensionCodeMap.get(confV.getCode()));
                    dataSource.getBaseInfoMap().put(confV.getCode(), allMeasureCodeMap.get(confV.getCode()));
                } else {
                    if (confV.getCode().contains("DIM_FAKE_")) {
                        pageData.getBaseInfoMap().put(confV.getCode(), confV);
                        if (null == dataSource.getDimConfMap().get(confV.getCode())) {
                            dataSource.getDimConfMap().put(confV.getCode(), confV);
                            dataSource.getDimConfList().add(confV);
                        }
                    }
                }
            }
        }
        for (Filter filterV : dataSource.getFilterList()) {
            if (filterV.getCode().contains("MEAS")) {
                if (null != allMeasureCodeMap.get(filterV.getCode())) {
                    pageData.getBaseInfoMap().put(filterV.getCode(), allMeasureCodeMap.get(filterV.getCode()));
                    dataSource.getBaseInfoMap().put(filterV.getCode(), allMeasureCodeMap.get(filterV.getCode()));
                }
            } else {
                if (null != allDimensionCodeMap.get(filterV.getCode())) {
                    pageData.getBaseInfoMap().put(filterV.getCode(), allDimensionCodeMap.get(filterV.getCode()));
                    dataSource.getBaseInfoMap().put(filterV.getCode(), allMeasureCodeMap.get(filterV.getCode()));
                }
            }
        }
    }


    @Override
    public void recordQuestInfo(String info, DataSource dataSource, PageData pageData) {
        String userName = UserThreadLocalUtil.getUserName();
        CompletableFuture.runAsync(() -> {
            recordQuestInfoRecord(userName, info, dataSource, pageData);
        });
    }

    @Override
    public PageData queryDetail(DataSource dataSource) {

        PageData pageData = new PageData();
        if (!dataSource.isData()) {
            pageData.setDataSource(dataSource);
            pageData.setRecordSuccess(false);
            return pageData;
        }
        Boolean flag = false;
        RelatedSet relatedSet = new RelatedSet();
        com.graphinsight.indicator.auto.entity.Measure originMeasure = null;
        for (BaseConfigure configure : dataSource.getDeleteMesa()) {

            if (flag) {
                break;
            }
            Map<Integer, List<MeasureApplication>> measureAppMap = cacheManager.getMetadataCache().getMeasIdAppList();
            if (null != measureAppMap.get(configure.getId().intValue())) {
                List<MeasureApplication> measureList = measureAppMap.get(configure.getId().intValue());
                for (MeasureApplication measureApplication : measureList) {
                    if (measureApplication.getApplyType() == 0) {
                        originMeasure = cacheManager.getMeasureCache(configure.getId().intValue()).getMeasure();
                        flag = true;
                        break;
                    }
                }
            }


        }
        if (null == originMeasure) {

            for (BaseConfigure configure : dataSource.getDimConfList()) {
                relatedSet.getDimensionSet().add(Math.toIntExact(configure.getId()));
            }

            for (Filter filter : dataSource.getFilterList()) {
                relatedSet.getDimensionSet().add(Math.toIntExact(filter.getId()));
            }
            relatedSet = bloodManager.listRelatedSet(relatedSet);

            TSpace spaces = itSpaceService.getAiSpaceById();
            Long spaceId = spaces.getId();

            String username = UserThreadLocalUtil.getUserName();
            UserContext userContext = userManager.getUserContext(spaceId, username);
            List<com.graphinsight.indicator.auto.entity.Measure> authMeasure = userContext.getAuthMeasures();

            if (relatedSet.getMeasureSet().isEmpty()) {
                pageData.setDataSource(dataSource);
                pageData.setRecordSuccess(false);
                pageData.setBaseInfoMap(new HashMap<>());
                buildBaseMap(dataSource, pageData);
                return pageData;
            }

            for (com.graphinsight.indicator.auto.entity.Measure measure : authMeasure) {
                if (measure.getOnline() == 0) {
                    continue;
                }
                if (flag) {
                    break;
                }

                if (relatedSet.getMeasureSet().contains(measure.getId())) {

                    Map<Integer, List<MeasureApplication>> measureAppMap = cacheManager.getMetadataCache().getMeasIdAppList();
                    if (null != measureAppMap.get(measure.getId())) {
                        List<MeasureApplication> measureList = measureAppMap.get(measure.getId());
                        for (MeasureApplication measureApplication : measureList) {
                            if (measureApplication.getApplyType() == 0) {
                                originMeasure = measure;
                                flag = true;
                                break;
                            }
                        }
                    }
                }
            }
        }
        if (null == originMeasure) {
            // 通过维度没有找到指标 查询详情明细 走模型识别
            dataSource.setRouteType("toDetail");

            pageData.setDataSource(dataSource);
            pageData.setRecordSuccess(false);
            pageData.setBaseInfoMap(new HashMap<>());
            buildBaseMap(dataSource, pageData);
            return pageData;
        } else {
            // 只有赛选条件，查询明细
            if (dataSource.getDimConfList().isEmpty()) {
                dataSource.setRouteType("toDetail");
                BaseConfigure configureMeas = new BaseConfigure();
                configureMeas.setCode(originMeasure.getCode());
                configureMeas.setName(originMeasure.getCnName());
                configureMeas.setIsHide(true);
                dataSource.setMeasureDetail(true);
                dataSource.getConfigureList().add(configureMeas);
                dataSource.setPageSize(CommonConstants.DETAIL_DEFAULT_COUNT);
                pageData = chartQueryService.execQuery(dataSource);
                pageData.setBaseInfoMap(new HashMap<>());
                buildBaseMap(dataSource, pageData);


                if (!pageData.getCellList().isEmpty()) {
                    List<Columns> columnsList = listDetailTableColumns(originMeasure.getCode());

                    Map<String, Columns> columnsMap = columnsList.stream()
                            .collect(Collectors.toMap(Columns::getColumnName, Function.identity(), (ex, re) -> ex, LinkedHashMap::new));

                    for (Cell cell : pageData.getCellList().get(0)) {
                        BaseConfigure baseConfigureHeader = new BaseConfigure();
                        if (null != columnsMap.get(cell.getCode())) {
                            Columns columns = columnsMap.get(cell.getCode());
                            if (!Objects.equals(columns.getColumnComment(), "")) {
                                baseConfigureHeader.setCode(columns.getColumnName());
                                baseConfigureHeader.setName(columns.getColumnComment());

                            } else {
                                baseConfigureHeader.setCode(cell.getCode());
                                baseConfigureHeader.setName(cell.getCode());
                            }

                        } else {
                            baseConfigureHeader.setCode(cell.getCode());
                            baseConfigureHeader.setName(cell.getCode());
                        }
                        dataSource.getHeaderConfList().add(baseConfigureHeader);
                    }

                    pageData.getCellList().remove(0);
                }

                pageData.setDataSource(dataSource);
            } else {
                dataSource.setRouteType("toSql");
                BaseConfigure configureMeas = new BaseConfigure();
                configureMeas.setCode(originMeasure.getCode());
                configureMeas.setName(originMeasure.getCnName());
                configureMeas.setIsHide(true);
                dataSource.getConfigureList().add(configureMeas);
                dataSource.setPageSize(CommonConstants.DETAIL_DEFAULT_COUNT);
                //此处将筛选条件也增加到分组条件中

                dataSource = this.addFilterToGroup(dataSource);

                pageData = chartQueryService.execQuery(dataSource);
                pageData.setBaseInfoMap(new HashMap<>());
                buildBaseMap(dataSource, pageData);
                pageData.setDataSource(dataSource);
            }


        }


        return pageData;
    }

    private DataSource addFilterToGroup(DataSource dataSource) {

        List<Filter> filterList = dataSource.getFilterList();
        if (!CollectionUtils.isEmpty(filterList)) {
            for (Filter filter : filterList) {
                BaseConfigure dimBaseConfig = new BaseConfigure();
                dimBaseConfig.setCode(filter.getCode());
                dimBaseConfig.setName(filter.getName());

                List configList = dataSource.getConfigureList();
                LinkedList linkedList = null;
                if (configList instanceof LinkedList) {
                    linkedList = (LinkedList) configList;
                    linkedList.addFirst(dimBaseConfig);
                } else {
                    linkedList = new LinkedList(configList);
                    linkedList.addFirst(dimBaseConfig);
                }

                dataSource.setConfigureList(linkedList);

            }
        }

        return dataSource;

    }

    public void recordQuestInfoRecord(String userName, String info, DataSource dataSource, PageData pageData) {
        String replyType = "success";
        if (pageData == null
                || pageData.getCellList().isEmpty()
                || dataSource == null
                || dataSource.getConfigureList().isEmpty()
                || !dataSource.getNoDataRangeList().isEmpty()) {
            replyType = "fail";
        }
        if (dataSource != null && dataSource.isBoard()) {
            replyType = "success";
        }
        AiQuestionInfo aiQuestionInfo = new AiQuestionInfo();
        aiQuestionInfo.setUser(userName);
        aiQuestionInfo.setContent(info);
        aiQuestionInfo.setReplyType(replyType);

        aiQuestionInfoMapper.insert(aiQuestionInfo);

    }

    private PageData buildPageData(List<Map<String, Object>> list, List<String> factList, DataSource dataSource, String executeSql) {

        PageData pageData = new PageData();

        pageData.setBaseInfoMap(new HashMap<>());
        LinkedList<List<Cell>> cellList = new LinkedList<>();
        List<Map<String, Object>> pageList = new LinkedList<>();
        Map<String, String> keyCodeMap = new HashMap<>();
        if (!CollectionUtils.isEmpty(list)) {

            for (int i = 0; i < list.size(); i++) {

                Map<String, Object> strObjMap = list.get(i);

                pageList.add(list.get(i));

                List<Cell> cells = new LinkedList<Cell>();

                int dimIndex = 0;

                for (Map.Entry<String, Object> entry : strObjMap.entrySet()) {
                    Cell dimCell = new Cell();
                    BaseConfigure dimConfigure = null;
                    if (dimIndex < dataSource.getConfigureList().size()) {
                        dimConfigure = dataSource.getConfigureList().get(dimIndex);
                    }
                    dimCell.setData(String.valueOf(entry.getValue()));
                    dimCell.setId(String.valueOf(entry.getValue()));
                    String cnName = String.valueOf(entry.getKey());
                    String code = "";

                    if (!(entry.getKey().toLowerCase().contains("count") || executeSql.toLowerCase().contains("sum"))) {
                        dimIndex++;
                    }

                    if (dimConfigure != null) {
                        cnName = dimConfigure.getName();
                        code = dimConfigure.getCode();
                    } else {
                        if (null != keyCodeMap.get(cnName)) {
                            code = keyCodeMap.get(cnName);
                        } else {
                            code = "DIM_FAKE_" + StringUtil.generateUUIDFromString(cnName).toString().replace("-", "");
                            keyCodeMap.put(cnName, code);

                        }
                    }

                    dimCell.setCode(code);
                    dimCell.setName(cnName);
                    dimCell.setType(CellType.DIMENSION);
                    cells.add(dimCell);

                }

                cellList.add(cells);
            }

        }

        for (Map.Entry<String, String> entry : keyCodeMap.entrySet()) {
            BaseConfigure baseConfigure = new BaseConfigure();
            baseConfigure.setName(entry.getKey());
            baseConfigure.setCode(entry.getValue());
            dataSource.getConfigureList().add(baseConfigure);
        }


        pageData.setRowList(pageList);
        pageData.setCellList(cellList);


        return pageData;
    }

    public PageData doSplitAction(DataQueryVO dataQueryVO, String word, Boolean isData) {


        User user = UserThreadLocalUtil.get();

        UserThreadLocalUtil.setBeginTime();
        // 获取分词元素值
        word = word.replaceAll("\\s+", "");
        //  替换
        word = word.replaceAll("近([" + CommonConstants.LARGE_NUMERALS + "]+|\\d+|[零壹贰叁肆伍陆柒捌玖]+)月", "近$1个月");
        List<Term> splitList = split(word);

        //WordSyntaxVo wordSyntaxVoList = wordSyntax.splitInfo(word);

        splitList = keyWordReplace(splitList);

        //

        UserThreadLocalUtil.printCost("splitList");

        DataSource dataSource = new DataSource();

        dataSource.setUsername(user.getUsername());

//        List<TSpace> tSpaceList = tSpaceMapper.selectList(Wrappers.<TSpace>lambdaQuery().orderByDesc(TSpace::getId));
        TSpace tSpace = itSpaceService.getAiSpaceById();
        dataSource.setSpaceId(tSpace.getId());

        RelatedCodeSet relatedCodeSet = new RelatedCodeSet();

        RelatedSet relatedSet = new RelatedSet();
        // 先找指标
        dataSource = getMeasureSource(splitList, dataSource, relatedCodeSet, relatedSet);

        if (dataSource.getConfigureList().isEmpty()) {
            return null;
        }

        UserThreadLocalUtil.printCost("getMeasureSource");
        // 检查数据权限
        PageData pageData = new PageData();
        Boolean isDataRange = checkDataRange(dataSource, user.getUsername());
        if (!isDataRange) {
            pageData.setDataRange(isDataRange);
            pageData.setDataAllRange(isDataRange);
            Set<String> tmpName = new HashSet<>();
            Set<String> codeSet = new HashSet<>();
            for (BaseConfigure conf : dataSource.getConfigureList()) {
                if (conf.getCode().contains("MEAS")) {
                    codeSet.add(conf.getCode());
                    tmpName.add(conf.getWordName());
                }
            }

            List<com.graphinsight.indicator.auto.entity.Measure> measurePage = measureMapper.selectList(Wrappers.<com.graphinsight.indicator.auto.entity.Measure>lambdaQuery().in(com.graphinsight.indicator.auto.entity.Measure::getCode, codeSet));

            Set<String> createBys = measurePage.stream().map(com.graphinsight.indicator.auto.entity.Measure::getCreateUser).collect(Collectors.toSet());

            List<User> userList = userManager.listUserByUsernames(createBys);
            // 获取管家信息
            String tip = "您尚未拥有当前指标（" + String.join("、", tmpName) + "）的权限，如需开通，请联系";
            Map<String, Object> rangeInfo = new HashMap<>();
            rangeInfo.put("tip", tip);
            rangeInfo.put("users", userList);
            pageData.setRangeInfo(rangeInfo);
            return pageData;
        }

        UserThreadLocalUtil.printCost("checkDataRange");

        splitList = removeSplitTerm(dataSource.getRemoveWordSet(), splitList);
        // 设置自然日期的维度，没有时间限制，默认为当前月份
        discernDateSource(splitList, word, dataSource, relatedCodeSet, relatedSet);

        // 指标同环比设置
        comparisonRate(word, dataSource);

        // 维度优先，条件其次
        splitList = removeSplitTerm(dataSource.getRemoveWordSet(), splitList);
        getAccurateConfigInfo(splitList, word, dataSource, relatedCodeSet, relatedSet);


        UserThreadLocalUtil.printCost("getAccurateConfigInfo");

        // 设别其余维度，每次识别到新维度，检查新维度是否有效，有效才记录；
        splitList = removeSplitTerm(dataSource.getRemoveWordSet(), splitList);
        List<String> needOtherWord = getNeedDiscernWord(splitList);
        // 识别其余过滤条件，每次识别到新维度，检查新维度是否有效，有效才记录；
        needOtherWord = removeSplitWord(dataSource.getRemoveWordSet(), needOtherWord);

        // 条件识别
        discernOtherFilterSource(needOtherWord, dataSource, relatedCodeSet);


        UserThreadLocalUtil.printCost("discernOtherFilterSource");

        if (isData) {
            Boolean flagIsLimit = false;
            Map<String, Object> baseInfoMap = new HashMap<>();
            dataSource.setSpaceId(tSpace.getId());
            if (null != dataQueryVO && null != dataQueryVO.getLimit()) {
                dataSource.setPageSize(dataQueryVO.getLimit());
            } else {
                Pattern pattern = Pattern.compile("(最高的|最低|最高|最低的|top)([" + CommonConstants.LARGE_NUMERALS + "]+|\\d+|[零壹贰叁肆伍陆柒捌玖]+)", Pattern.CASE_INSENSITIVE);
                Matcher matcher = pattern.matcher(word);
                if (matcher.find()) {
                    if (null != matcher.group(2)) {
                        dataSource.setPageSize(getDateBeforeNumber(matcher.group(2)));
                        flagIsLimit = true;
                    } else {
                        dataSource.setPageSize(2000);
                    }
                } else {
                    dataSource.setPageSize(2000);
                }
            }

            // 根据dataSource构建缓存
            Boolean isExecQuery = false;
            String cacheDataSourceKey = buildMd5Key(dataSource, "DATAQUERY_");

            List<String> showBiList = aiShowIndicatorMapper.getAllShow();
            dataSource.setChartShow(true);
            for (BaseConfigure confTemp : dataSource.getConfigureList()) {
                if (confTemp.getCode().contains("MEAS")) {
                    if (!showBiList.contains(confTemp.getCode())) {
                        dataSource.setChartShow(false);
                        break;
                    }
                }

            }

            if (redisCacheService.hasKey(cacheDataSourceKey)) {
                pageData = redisCacheService.get(cacheDataSourceKey, PageData.class);
            } else {
                pageData = chartQueryService.execQuery(dataSource);
                // todo 简单的缓存处理 每天凌晨失效
                isExecQuery = true;
            }
            dataSource.setDataCache(!isExecQuery);

            //pageData = chartQueryService.execQuery(dataSource);


            MetadataCache metadataCache = cacheManager.getMetadataCache();
            Map<String, com.graphinsight.indicator.auto.entity.Measure> allMeasureCodeMap = metadataCache.getAllMeasureCodeMap();
            Map<String, com.graphinsight.indicator.auto.entity.Dimension> allDimensionCodeMap = metadataCache.getAllDimensionCodeMap();


            List<String> measureList = new ArrayList<>();
            List<String> dimInfoList = new ArrayList<>();

            List<Long> measureIdList = new ArrayList<>();

            dataSource.getConfigureList().forEach(confV -> {
                if (confV.getCode().contains("MEAS")) {
                    measureList.add(confV.getCode());
                    measureIdList.add(confV.getId());
                    if (null != allMeasureCodeMap.get(confV.getCode())) {
                        baseInfoMap.put(confV.getCode(), allMeasureCodeMap.get(confV.getCode()));
                    }
                } else {
                    dimInfoList.add(confV.getCode());
                    if (null != allDimensionCodeMap.get(confV.getCode())) {
                        baseInfoMap.put(confV.getCode(), allDimensionCodeMap.get(confV.getCode()));
                    }
                }
            });

            dataSource.getFilterList().forEach(filterV -> {
                if (filterV.getCode().contains("MEAS")) {
                    if (null != allMeasureCodeMap.get(filterV.getCode())) {
                        baseInfoMap.put(filterV.getCode(), allMeasureCodeMap.get(filterV.getCode()));
                    }
                } else {
                    if (null != allDimensionCodeMap.get(filterV.getCode())) {
                        baseInfoMap.put(filterV.getCode(), allDimensionCodeMap.get(filterV.getCode()));
                    }
                }
            });

            Integer queryType = 0;
            if (dimInfoList.size() > 0) {
                queryType = 3;
            } else {
                if (measureList.size() == 1) {
                    queryType = 2;
                } else if (measureList.size() > 1) {
                    queryType = 4;
                }
            }

            if (measureList.size() > 1) {
                queryType = 4;
            }

            if (flagIsLimit) {
                queryType = 5;
            }
            if (word.contains("占比")) {
                queryType = 6;
            }
            dataSource.setQueryType(queryType);
            // 记录数据到相应表

            pageData.setBaseInfoMap(baseInfoMap);
            pageData.setDataSource(dataSource);
            pageData.setSpaceId(Arrays.asList(itSpaceService.getAiSpaceById().getId()));
            // todo 目前写死根据指标获取解读
            // new QueryWrapper<AiMeasTemplate>().lambda().in(AiMeasTemplate::getMeasId, measureIdList)
            List<AiMeasTemplate> aiMeasTemplates = aiMeasTemplateMapper.selectList(null);
            if (!aiMeasTemplates.isEmpty()) {
                pageData.setExplainTemplate(aiMeasTemplates.get(0).getContent());
            }
            // 缓存记录
            // todo 简单的缓存处理 每天凌晨失效
            if (isExecQuery && !pageData.getCellList().isEmpty() && pageData.getDataAllRange()) {
                redisCacheService.put(cacheDataSourceKey, pageData, getRemainSecondsOneDay(new Date()));
                UserThreadLocalUtil.printCost("pageData");
            }
            return pageData;
        } else {
            pageData.setDataSource(dataSource);
            return pageData;
        }
    }


    public boolean isSuperAdmin(String username) {
        if (redisCacheService.hasKey(cache_pre_range_admin + username)) {
            return redisCacheService.get(cache_pre_range_admin + username, Boolean.class);
        }
        List<TSuperAdmin> tSuperAdmins = superAdminService.list(Wrappers.<TSuperAdmin>lambdaQuery().eq(TSuperAdmin::getEmpCode, username));
        redisCacheService.put(cache_pre_range_admin + username, !CollectionUtils.isEmpty(tSuperAdmins), 2 * 60 * 60);
        return !CollectionUtils.isEmpty(tSuperAdmins);
    }

    public Boolean checkDataRange(DataSource dataSource, String username) {
        // 缓存2h
        // 超级管理员

        if (isSuperAdmin(username)) {
            return true;
        }
        // 缓存key

        if (redisCacheService.hasKey(cache_pre_range + username)) {
            List<String> dataRangeCode = redisCacheService.get(cache_pre_range + username, List.class);
            for (BaseConfigure configure : dataSource.getConfigureList()) {
                if (configure.getCode().contains("MEAS_LDX_")) {
                    return true;
                }
                if (configure.getCode().contains("MEAS")) {
                    if (!dataRangeCode.contains(configure.getCode())) {
                        return false;
                    }
                }
            }
            return true;
        }


        UserContext userContext = userManager.getUserContext(itSpaceService.getAiSpaceById().getId(), username);

//        userContext.getAuthMeasures().stream()
        List<String> authMeasCodes = new ArrayList<>();
        if (null != userContext && null != userContext.getAuthMeasures()) {
            for (com.graphinsight.indicator.auto.entity.Measure measureItem : userContext.getAuthMeasures()) {
                if (null != measureItem && null != measureItem.getCode()) {
                    authMeasCodes.add(measureItem.getCode());
                }
            }
        }

        Boolean dataRangeFlag = true;
        List<String> dataRangeCodeList = new ArrayList<>();
        for (BaseConfigure configure : dataSource.getConfigureList()) {
            if (configure.getCode().contains("MEAS_LDX_")) {
                dataRangeFlag = true;
            }
            if (configure.getCode().contains("MEAS")) {
                if (!authMeasCodes.contains(configure.getCode())) {
                    dataRangeFlag = false;
                }
            }
        }

        if (!authMeasCodes.isEmpty()) {
            redisCacheService.put(cache_pre_range + username, authMeasCodes, 2 * 60 * 60);
        }
        return dataRangeFlag;
    }

    public Integer getRemainSecondsOneDay(Date currentDate) {
        //使用plusDays加传入的时间加1天，将时分秒设置成0
        LocalDateTime midnight = LocalDateTime.ofInstant(currentDate.toInstant(),
                        ZoneId.systemDefault()).plusDays(1).withHour(0).withMinute(0)
                .withSecond(0).withNano(0);
        LocalDateTime currentDateTime = LocalDateTime.ofInstant(currentDate.toInstant(),
                ZoneId.systemDefault());
        //使用ChronoUnit.SECONDS.between方法，传入两个LocalDateTime对象即可得到相差的秒数
        long seconds = ChronoUnit.SECONDS.between(currentDateTime, midnight);
        return (int) seconds;
    }

    void recordAnalysisInfo(String content, DataSource dataSource, Map<String, Object> baseInfoMap) {

        if (null != getSearchContent(content)) {
            return;
        }
        AiAnalysisContext aiAnalysisContext = new AiAnalysisContext();
        aiAnalysisContext.setContentCode(StringUtil.generateUUIDFromString(content).toString().replaceAll("-", ""));
        aiAnalysisContext.setAnalysisContent(JSONObject.toJSONString(dataSource));
        aiAnalysisContext.setBaseInfo(JSONObject.toJSONString(baseInfoMap));
        aiAnalysisContextMapper.insert(aiAnalysisContext);
    }

    private AiAnalysisContext getSearchContent(String content) {
        String contentCode = StringUtil.generateUUIDFromString(content).toString().replaceAll("-", "");
        LambdaQueryWrapper<AiAnalysisContext> queryCollectInfo = new QueryWrapper<AiAnalysisContext>().lambda();
        queryCollectInfo.eq(AiAnalysisContext::getContentCode, contentCode);
        List<AiAnalysisContext> aiAnalysisContextList = aiAnalysisContextMapper.selectList(queryCollectInfo);
        if (aiAnalysisContextList.isEmpty()) {
            return null;
        }
        return aiAnalysisContextList.get(0);
    }


    @Override
    public PageData doRecommendData(DataSource dataSource) {

        //DataSource dataSource = SerializationUtils.clone(dataSource);

        PageData pageData = new PageData();
        String cacheRecommendKey = buildMd5Key(dataSource, "RECOMMEND_");
        if (redisCacheService.hasKey(cacheRecommendKey)) {
            pageData = redisCacheService.get(cacheRecommendKey, PageData.class);
            return pageData;
        }
        dataSource.getConfigureList().removeIf(baseInfo -> baseInfo.getCode().contains("DIM"));
        List<String> measureList = dataSource.getConfigureList().stream().map(BaseConfigure::getCode).collect(Collectors.toList());
        // todo 处理第一个
        if (measureList.isEmpty()) {
            return null;
        }
        // 查询关联的
        RelatedCodeSet relatedCodeSet = new RelatedCodeSet();
        relatedCodeSet.getMeasureSet().add(measureList.get(0));

        dataSource.getFilterList().forEach(filter -> {
            relatedCodeSet.getDimensionSet().add(filter.getCode());
        });
        RelatedCodeSet relateCheckTemp = listRelatedSetDemo(relatedCodeSet);

        List<MeasureRelateRecode> measureRelateRecodes = measureRelateRecodeMapper.relateRecodeInfo(measureList.get(0), relateCheckTemp.getMeasureSet());

        dataSource.getConfigureList().clear();

        Map<String, String> relateNumMap = new HashMap<>();
        measureRelateRecodes.forEach(info -> {
            BaseConfigure baseConfigure = new BaseConfigure();
            baseConfigure.setCode(info.getRCode());
            dataSource.getConfigureList().add(baseConfigure);
            relateNumMap.put(info.getRCode(), info.getMData());
        });

        pageData = chartQueryService.execQuery(dataSource);

        MetadataCache metadataCache = cacheManager.getMetadataCache();
        Map<String, com.graphinsight.indicator.auto.entity.Measure> allMeasureCodeMap = metadataCache.getAllMeasureCodeMap();
        Map<String, com.graphinsight.indicator.auto.entity.Dimension> allDimensionCodeMap = metadataCache.getAllDimensionCodeMap();

        Map<String, Object> baseInfoMap = new HashMap<>();
        dataSource.setSpaceId(itSpaceService.getAiSpaceById().getId());
        dataSource.getConfigureList().forEach(confV -> {
            if (confV.getCode().contains("MEAS")) {

                if (null != allMeasureCodeMap.get(confV.getCode())) {
                    baseInfoMap.put(confV.getCode(), allMeasureCodeMap.get(confV.getCode()));
                }
            } else {
                if (null != allDimensionCodeMap.get(confV.getCode())) {
                    baseInfoMap.put(confV.getCode(), allDimensionCodeMap.get(confV.getCode()));
                }
            }
        });

        dataSource.getFilterList().forEach(filterV -> {
            if (filterV.getCode().contains("MEAS")) {
                if (null != allMeasureCodeMap.get(filterV.getCode())) {
                    baseInfoMap.put(filterV.getCode(), allMeasureCodeMap.get(filterV.getCode()));
                }
            } else {
                if (null != allDimensionCodeMap.get(filterV.getCode())) {
                    baseInfoMap.put(filterV.getCode(), allDimensionCodeMap.get(filterV.getCode()));
                }
            }
        });

        pageData.getCellList().forEach(cells -> {
            cells.forEach(cell -> {
                if (cell.getCode().contains("MEAS")) {
                    if (null != relateNumMap.get(cell.getCode())) {
                        cell.setRelateFactorNum(relateNumMap.get(cell.getCode()).substring(0, 6));
                    }
                }
            });
        });
        pageData.setBaseInfoMap(baseInfoMap);
        pageData.setDataSource(dataSource);
        pageData.setSpaceId(Arrays.asList(itSpaceService.getAiSpaceById().getId()));

        redisCacheService.put(cacheRecommendKey, pageData, getRemainSecondsOneDay(new Date()));

        return pageData;
    }

    private String buildMd5Key(DataSource dataSource, String cachePre) {

        String cacheKey = "";

        for (BaseConfigure configure : dataSource.getConfigureList()) {

            if (configure.getExpression() != null) {
                cacheKey += configure.getExpression();
            } else {
                cacheKey += configure.getCode();
            }
            if (configure.getOrder() != null) {
                cacheKey += configure.getOrder().getSortType();
            }

            if (!configure.getRatioList().isEmpty()) {
                for (Ratio ratio : configure.getRatioList()) {
                    cacheKey += ratio.getRatioType() + ratio.getDimCode();
                }
            }
        }

        for (Filter filter : dataSource.getFilterList()) {
            cacheKey += filter.getCode();
            for (Operator operator : filter.getOperatorList()) {
                cacheKey += operator.getDataList().toString() + operator.getSqlOprType();
            }
        }
        cacheKey += dataSource.getPageSize();
        String keyStr = StringUtil.generateUUIDFromString(cacheKey).toString().replaceAll("-", "");

        String md5Key = cachePre + keyStr;


        return md5Key;

    }


    public DataSource comparisonRate(String word, DataSource dataSource) {

        BaseConfigure dateConfig = null;

        if (!word.contains("同比") && !word.contains("环比")) {
            return dataSource;
        }
        for (BaseConfigure baseConfigure : dataSource.getConfigureList()) {
            if (baseConfigure.getCode().contains("DIM")) {
                dateConfig = baseConfigure;
                break;
            }
        }

        if (null == dateConfig) {
            for (Filter filter : dataSource.getFilterList()) {
                if (null != filter.getViewType() && ViewType.isDate(filter.getViewType().getValue())) {
                    dateConfig = new BaseConfigure();
                    dateConfig.setCode(filter.getCode());
                    dateConfig.setName(filter.getName());
                    dataSource.getConfigureList().add(dateConfig);
                    break;
                }
            }
        }

        if (null == dateConfig) {
            return dataSource;
        }

        // 没有时间维度，不支持同环比
        if (!dateConfig.getCode().contains("DIM")) {
            return dataSource;
        }

        for (BaseConfigure baseConfigure : dataSource.getConfigureList()) {

            if (!baseConfigure.getCode().contains("MEAS")) {
                continue;
            }
            dataSource.getRemoveWordSet().add("同比");
            dataSource.getRemoveWordSet().add("环比");

            Map<String, Integer> rateDateMap = CommonConstants.rateDateMap.get(dateConfig.getWordName());


            List<Ratio> ratioList = new ArrayList<>();
            Ratio ratio = new Ratio();
            if (null != rateDateMap.get(dateConfig.getWordName() + "同比")) {

                ratio.setDimCode(dateConfig.getCode());

                RatioType ratioType = RatioType.getTypeByCode(rateDateMap.get(dateConfig.getWordName() + "同比"));

                ratio.setRatioType(ratioType);

                Map<String, Integer> settingsMap = new HashedMap();
                settingsMap.put("type", ratioType.getCode());

                ratio.setSettings(JSON.toJSONString(settingsMap));
                ratioList.add(ratio);
            }

            if (null != rateDateMap.get(dateConfig.getWordName() + "环比")) {
                ratio = new Ratio();
                ratio.setDimCode(dateConfig.getCode());

                RatioType ratioType = RatioType.getTypeByCode(rateDateMap.get(dateConfig.getWordName() + "环比"));

                ratio.setRatioType(ratioType);

                Map<String, Integer> settingsMap = new HashedMap();
                settingsMap.put("type", ratioType.getCode());

                ratio.setSettings(JSON.toJSONString(settingsMap));
                ratioList.add(ratio);
            }
            if (!ratioList.isEmpty()) {
                ValueFormat valueFormat = new ValueFormat();
                valueFormat.setFormatType(FormatType.DECIMAL);
                valueFormat.setValue(4);
                baseConfigure.setValueFormat(valueFormat);
                baseConfigure.setMeasureTypeFlag("rate");
                baseConfigure.setRatioList(ratioList);
                Map<String, Object> stringObjectMap = getRatioRangeTimeConfig(dataSource);
                dataSource.setRatioRangeTime(stringObjectMap);
                dataSource.setSingle(true);
            }
        }

        return dataSource;
    }


    @Autowired
    WordCarInfosMapper wordCarInfosMapper;

    public DataSource discernRegularFilterSource(List<String> splitWord, DataSource dataSource, RelatedCodeSet relatedCodeSet) {
        List<WordCarInfos> wordCarInfosList = wordCarInfosMapper.selectKeyList(splitWord);
        // 获取车型的适配维度
        RelatedCodeSet haveRelateCodeInfo = listRelatedSetDemo(relatedCodeSet);
        List<com.graphinsight.indicator.auto.entity.Dimension> dimensionList = dimensionMapper.selectListByName(wordCarInfosList.stream().map(WordCarInfos::getUseValue).collect(Collectors.toList()), haveRelateCodeInfo.getDimensionSet());

        if (dimensionList.isEmpty()) {
            return dataSource;
        }
        Map<String, com.graphinsight.indicator.auto.entity.Dimension> dimensionMap = dimensionList.stream()
                .collect(Collectors.toMap(com.graphinsight.indicator.auto.entity.Dimension::getCnName, Function.identity(), (ex, re) -> ex, LinkedHashMap::new));

        for (WordCarInfos termItem : wordCarInfosList) {

            com.graphinsight.indicator.auto.entity.Dimension dimensionTemp = null;

            if (null != dimensionMap.get(termItem.getUseValue())) {
                dimensionTemp = dimensionMap.get(termItem.getUseValue());
            } else {
                Set<String> likeValueSet = dimensionMap.keySet();
                for (String likeKey : likeValueSet) {
                    // 只获取一个 取权重高的
                    if (likeKey.contains(termItem.getUseValue())) {
                        dimensionTemp = dimensionMap.get(likeKey);
                        break;
                    }
                }
            }
            if (null != dimensionTemp) {
                //
                relatedCodeSet.getDimensionSet().add(dimensionTemp.getCode());
                RelatedCodeSet relateCheckTemp = listRelatedSetDemo(relatedCodeSet);
                // 如果为空，说明识别的该条件下的维度不满足，剔除后，继续识别
                if (null == relateCheckTemp || relateCheckTemp.getDimensionSet().isEmpty() || relateCheckTemp.getMeasureSet().isEmpty()) {
                    relatedCodeSet.getDimensionSet().remove(dimensionTemp.getCode());
                } else {

                    BaseConfigure baseConfigure = new BaseConfigure();
                    baseConfigure.setCode(dimensionTemp.getCode());
                    dataSource.getConfigureList().add(baseConfigure);
                    dataSource.getRemoveWordSet().add(termItem.getOriginalValue());

                    // 如果下一个词属于条件内容，设置上条件

                    Filter filter = new Filter();
                    filter.setCode(dimensionTemp.getCode());
                    Operator operator = new Operator();
                    operator.setSqlOprType(SqlOprType.IN);
                    operator.setDataList(Arrays.asList(termItem.getOriginalValue()));
                    filter.setOperatorList(Collections.singletonList(operator));
                    dataSource.getFilterList().add(filter);
                    dataSource.getRemoveWordSet().add(termItem.getOriginalValue());


                }


            }

        }

        return dataSource;


    }

    public DataSource discernOtherFilterSource(List<String> splitWord, DataSource dataSource, RelatedCodeSet relatedCodeSet) {
        // 通过识别到的指标 与维度，获取可用的维度
        Map<String, DimAllValuesInfo> dimAllValuesMap = getValidConfigInfo(splitWord, null, relatedCodeSet);
        if (null == dimAllValuesMap || dimAllValuesMap.isEmpty()) {
            return dataSource;
        }
        List<String> removeWordList = new ArrayList<>();
        DimAllValuesInfo dimAllValuesInfoItem = null;
        Map<String, List<String>> filterInfoMap = new HashMap<>();
        // 检查维度是否有效
        for (String splitInfo : splitWord) {
            if (splitInfo.length() <= 1) {
                continue;
            }
            // 优先取等于的，其次是包含的
            if (null != dimAllValuesMap.get(splitInfo)) {
                // 设置条件
                dimAllValuesInfoItem = dimAllValuesMap.get(splitInfo);
                if (null != dimAllValuesInfoItem) {
                    // 设别到的dim_cdoe~value_key 取第一个 有维度，有条件的

                    String[] dimFilterList = dimAllValuesInfoItem.getDimFilters().split(",");

                    for (int i = 0; i < dimFilterList.length; i++) {
                        String[] dimFilterItem = dimFilterList[i].split("~");
                        if (dimFilterItem.length > 2) {

                            relatedCodeSet.getDimensionSet().add(dimFilterItem[1]);
                            RelatedCodeSet relateCheckTemp = listRelatedSetDemo(relatedCodeSet);

                            // 如果为空，说明识别的该条件下的维度不满足，剔除后，继续识别
                            if (null == relateCheckTemp || relateCheckTemp.getDimensionSet().isEmpty() || relateCheckTemp.getMeasureSet().isEmpty()) {
                                relatedCodeSet.getDimensionSet().remove(dimFilterItem[1]);
                            } else {
                                // 如果识别成功，只取识别到的第一个，后续不在处理，可以按照权重再去优化
                                List<String> filterValue = filterInfoMap.computeIfAbsent(dimFilterItem[1], k -> new ArrayList<>());
                                filterValue.add(dimFilterItem[2]);
                                break;
                            }

                        }
                    }


                }
            } else {
                Set<String> likeValueSet = dimAllValuesMap.keySet();
                for (String likeKey : likeValueSet) {
                    // 只获取一个
                    if (likeKey.contains(splitInfo)) {
                        dimAllValuesInfoItem = dimAllValuesMap.get(likeKey);

                        if (null != dimAllValuesInfoItem) {
                            // 设别到的dim_cdoe~value_key 取第一个 有维度，有条件的

                            String[] dimFilterList = dimAllValuesInfoItem.getDimFilters().split(",");

                            for (int i = 0; i < dimFilterList.length; i++) {
                                String[] dimFilterItem = dimFilterList[i].split("~");
                                if (dimFilterItem.length > 2) {

                                    relatedCodeSet.getDimensionSet().add(dimFilterItem[1]);
                                    RelatedCodeSet relateCheckTemp = listRelatedSetDemo(relatedCodeSet);
                                    // 如果为空，说明识别的该条件下的维度不满足，剔除后，继续识别
                                    if (null == relateCheckTemp || relateCheckTemp.getDimensionSet().isEmpty() || relateCheckTemp.getMeasureSet().isEmpty()) {
                                        relatedCodeSet.getDimensionSet().remove(dimFilterItem[1]);
                                    } else {
                                        // 如果识别成功，只取识别到的第一个，后续不在处理，可以按照权重再去优化
                                        List<String> filterValue = filterInfoMap.computeIfAbsent(dimFilterItem[1], k -> new ArrayList<>());
                                        filterValue.add(dimFilterItem[2]);
                                        break;
                                    }

                                }
                            }


                        }
                    }
                }
            }


        }
        if (!filterInfoMap.isEmpty()) {
            // 剔除掉默认设置的时间维度
            if (!dataSource.isNatureTimeDefault()) {
                dataSource.getFilterList().removeIf(filter -> dateDefaultList.contains(filter.getCode()));
            }

            // 检查时间维度是否为默认
            if (!dataSource.isDateExit() && !dataSource.isSingle()) {
                dataSource.getConfigureList().removeIf(baseConfigure -> dateDefaultList.contains(baseConfigure.getCode()));
            }

            filterInfoMap.forEach((fk, fv) -> {
                BaseConfigure baseConfigure = new BaseConfigure();
                baseConfigure.setCode(fk);
                dataSource.getConfigureList().add(baseConfigure);
                Filter filter = new Filter();
                Dimension dimensionInfo = indicatorService.getDimensionTableInfo(fk);
                filter.setCode(fk);
                filter.setName(dimensionInfo.getName());
                Operator operator = new Operator();
                operator.setSqlOprType(SqlOprType.IN);
                operator.setDataList(fv);
                filter.setOperatorList(Collections.singletonList(operator));
                dataSource.getFilterList().add(filter);
            });

        }

        return dataSource;
    }

    public List<String> getNeedDiscernWord(List<Term> splitList) {
        // 查询可用维度
        List<String> otherDictSplit = new ArrayList<>();
        for (Term termItem : splitList) {
            if (termItem.getNatureStr().contains("uj")
                    || Objects.equals(termItem.getNatureStr(), "c")
                    || Objects.equals(termItem.getNatureStr(), "m")) {
                continue;
            }
            // 特殊处理没有值的
            otherDictSplit.add(termItem.getName());
//            if (null != CommonConstants.CarTypeMap.get(termItem.getName())) {
//                otherDictSplit.add(CommonConstants.CarTypeMap.get(termItem.getName()));
//            } else {
//                otherDictSplit.add(termItem.getName());
//            }
        }
        return otherDictSplit;
    }


    private List<Term> keyWordReplace(List<Term> splits) {
        List<WordValues> wordValuesList = wordValuesMapper.selectKeyList(splits.stream().map(Term::getName).collect(Collectors.toList()));

        Map<String, WordValues> wordValuesMap = wordValuesList.stream().collect(Collectors.toMap(WordValues::getValue, Function.identity(), (ex, re) -> ex));

        List<WordInfos> wordInfosList = wordInfosMapper.selectKeyList(splits.stream().map(Term::getName).collect(Collectors.toList()));

        Map<String, WordInfos> wordInfosMap = wordInfosList.stream().collect(Collectors.toMap(WordInfos::getOriginalValue, Function.identity(), (ex, re) -> ex));


        for (int i = 0; i < splits.size(); i++) {
            if (null != wordValuesMap.get(splits.get(i).getName())) {
                splits.get(i).setName(wordValuesMap.get(splits.get(i).getName()).getKey());
            }
            if (null != wordInfosMap.get(splits.get(i).getName())) {
                splits.get(i).setName(wordInfosMap.get(splits.get(i).getName()).getUseValue());
            }
        }
        return splits;

    }


    @Autowired
    DimensionMapper dimensionMapper;


    public DataSource getAccurateConfigInfo(List<Term> splitList, String word, DataSource dataSource, RelatedCodeSet relatedCodeSet, RelatedSet relatedSet) {

        if (splitList.isEmpty()) {
            return dataSource;
        }


        RelatedCodeSet haveRelateCodeInfo = listRelatedSetDemo(relatedCodeSet);
        List<com.graphinsight.indicator.auto.entity.Dimension> dimensionList = dimensionMapper.selectListByName(splitList.stream().map(Term::getName).collect(Collectors.toList()), haveRelateCodeInfo.getDimensionSet());

        if (dimensionList.isEmpty()) {
            return dataSource;
        }
        Map<String, com.graphinsight.indicator.auto.entity.Dimension> dimensionMap = dimensionList.stream().collect(Collectors.toMap(dimItem -> dimItem.getCnName().toLowerCase(), dimItem -> dimItem, (ex, re) -> ex, LinkedHashMap::new));

        for (Term termItem : splitList) {
            if (termItem.getNatureStr().contains("uj")
                    || termItem.getNatureStr().contains("c")
                    || termItem.getName().length() <= 1
            ) {
                continue;
            }
            com.graphinsight.indicator.auto.entity.Dimension dimensionTemp = null;

            if (null != dimensionMap.get(termItem.getName())) {
                dimensionTemp = dimensionMap.get(termItem.getName());
            } else {
                Set<String> likeValueSet = dimensionMap.keySet();
                for (String likeKey : likeValueSet) {
                    // 只获取一个 取权重高的
                    if (likeKey.contains(termItem.getName())) {
                        dimensionTemp = dimensionMap.get(likeKey);
                        if (dimensionTemp.getViewType() == 0 || !dataSource.isDateExit()) {
                            break;
                        } else {
                            dimensionTemp = null;
                        }
                    }
                }
            }
            if (null != dimensionTemp) {
                //
                relatedCodeSet.getDimensionSet().add(dimensionTemp.getCode());
                RelatedCodeSet relateCheckTemp = listRelatedSetDemo(relatedCodeSet);
                // 如果为空，说明识别的该条件下的维度不满足，剔除后，继续识别
                if (null == relateCheckTemp || relateCheckTemp.getDimensionSet().isEmpty() || relateCheckTemp.getMeasureSet().isEmpty()) {
                    relatedCodeSet.getDimensionSet().remove(dimensionTemp.getCode());
                } else {

                    BaseConfigure baseConfigure = new BaseConfigure();
                    baseConfigure.setCode(dimensionTemp.getCode());
                    baseConfigure.setName(dimensionTemp.getCnName());
                    baseConfigure.setCodeAlias(dimensionTemp.getCode());
                    dataSource.getConfigureList().add(baseConfigure);
                    dataSource.getRemoveWordSet().add(termItem.getName());

                    // 如果下一个词属于条件内容，设置上条件
                    if (eqWordList.contains(termItem.to().getName())) {
                        DimAllValuesInfo dimAllValuesInfoItem = dimAllValuesMapper.selectInfoByDimAndValue(dimensionTemp.getCode(), termItem.to().to().getName());
                        if (null != dimAllValuesInfoItem) {
                            Filter filter = new Filter();
                            filter.setCode(dimensionTemp.getCode());
                            filter.setName(dimensionTemp.getCnName());
                            Operator operator = new Operator();
                            operator.setSqlOprType(SqlOprType.IN);
                            operator.setDataList(Arrays.asList(dimAllValuesInfoItem.getValueKey()));
                            filter.setOperatorList(Collections.singletonList(operator));
                            dataSource.getFilterList().add(filter);
                            dataSource.getRemoveWordSet().add(termItem.to().getName());
                            dataSource.getRemoveWordSet().add(termItem.to().to().getName());
                        }

                    }

                }


            }

        }

        if (dataSource.getConfigureList().size() > 1) {
            // 检查时间维度是否为默认
            if (!dataSource.isDateExit() && !dataSource.isSingle()) {
                dataSource.getConfigureList().removeIf(baseConfigure -> dateDefaultList.contains(baseConfigure.getCode()));
            }
        }


        return dataSource;

    }

    public Map<String, DimAllValuesInfo> getValidConfigInfo(List<String> splitLikeWord, List<String> dictWord, RelatedCodeSet relatedCodeSet) {
        // 通过识别到的指标 与维度，获取可用的维度
        try {
            RelatedCodeSet relatedCodeResTemp = listRelatedSetDemo(relatedCodeSet);
            if (relatedCodeResTemp.getDimensionSet().isEmpty()) {
                return null;
            }
            List<DimAllValuesInfo> validDimList = dimAllValuesMapper.selectListByLike(splitLikeWord, relatedCodeResTemp.getDimensionSet(), dictWord);

            Map<String, DimAllValuesInfo> dimAllValuesMap = validDimList.stream().collect(Collectors.toMap(DimAllValuesInfo::getValueText, Function.identity(), (re, ex) -> re, LinkedHashMap::new));
            return dimAllValuesMap;
        } catch (Exception e) {
            log.info("valid config is {}", e);
            return null;
        }

    }

    public DataSource discernOtherDimSource(List<String> splitWord, DataSource dataSource, RelatedCodeSet relatedCodeSet) {

        Map<String, String> dictMap = new HashMap<>();
        List<String> dictList = new ArrayList<>();
        splitWord.forEach(originName -> {
            if (null != CommonConstants.CarTypeMap.get(originName)) {
                dictMap.put(originName, CommonConstants.CarTypeMap.get(originName));
                dictList.add(CommonConstants.CarTypeMap.get(originName));
            }
        });

        if (dictMap.isEmpty()) {
            return dataSource;
        }

        // 通过识别到的指标 与维度，获取可用的维度
        Map<String, DimAllValuesInfo> dimAllValuesMap = getValidConfigInfo(splitWord, dictList, relatedCodeSet);
        if (null == dimAllValuesMap || dimAllValuesMap.isEmpty()) {
            return dataSource;
        }


        // 检查维度是否有效
        dictMap.forEach((dictK, splitInfo) -> {

            if (null == dimAllValuesMap.get(splitInfo)) {
                return;
            }
            String[] dimFilterList = dimAllValuesMap.get(splitInfo).getDimFilters().split(",");
            for (int i = 0; i < dimFilterList.length; i++) {
                String[] dimFilterItem = dimFilterList[i].split("~");
                relatedCodeSet.getDimensionSet().add(dimFilterItem[0]);
                RelatedCodeSet relateCheckTemp = listRelatedSetDemo(relatedCodeSet);
                // 如果为空，说明识别的该维度不满足，剔除后，继续识别
                if (null == relateCheckTemp || relateCheckTemp.getDimensionSet().isEmpty() || relateCheckTemp.getMeasureSet().isEmpty()) {
                    relatedCodeSet.getDimensionSet().remove(dimFilterItem[0]);
                } else {
                    BaseConfigure baseConfigure = new BaseConfigure();
                    baseConfigure.setCode(dimFilterItem[0]);
                    dataSource.getConfigureList().add(baseConfigure);
                    dataSource.getRemoveWordSet().add(dictK);
                }
            }
        });


        return dataSource;
    }


    public List<BaseInfoDTO> listDateDimensionById(Set<Integer> dimensionCodes, Set<Integer> measureCodes) {
        RelatedSet relatedSet = new RelatedSet();
        relatedSet.setMeasureSet(measureCodes);
        relatedSet.setDimensionSet(dimensionCodes);
        RelatedSet rs = bloodManager.listDateTypeDimension(relatedSet);
        Set<Integer> dimensionSet = rs.getDimensionSet();
        List<BaseInfoDTO> dimensionList = dimensionSet.stream()
                .map(dimId -> cacheManager.getDimensionCache(dimId))
                .map(cache -> {
                    BaseInfoDTO dimension = new BaseInfoDTO();
                    dimension.setEnName(cache.getDimension().getEnName());
                    dimension.setCnName(cache.getDimension().getCnName());
                    dimension.setCode(cache.getDimension().getCode());
                    dimension.setViewType(cache.getDimension().getViewType());
                    return dimension;
                }).collect(Collectors.toList());
        return dimensionList;
    }

    public DataSource discernDateSource(List<Term> splitList, String word, DataSource dataSource, RelatedCodeSet relatedCodeSet, RelatedSet relatedSet) {

        List<BaseInfoDTO> dateList = listDateDimensionById(relatedSet.getDimensionSet(), relatedSet.getMeasureSet());

        Map<Integer, List<BaseInfoDTO>> groupByCategory = dateList.stream()
                .collect(Collectors.groupingBy(BaseInfoDTO::getViewType));

        DateTimeFormatter formatter = null;
        // 时间维度的map 3，5,1
        Map<String, BaseInfoDTO> dateDimInfoMap = new HashMap<>();
        // 月的维度信息
        BaseInfoDTO monthDimInfo = null;
        if (null != groupByCategory.get(3) && !groupByCategory.get(3).isEmpty()) {
            List<BaseInfoDTO> monthDimList = groupByCategory.get(3);
            monthDimInfo = monthDimList.stream().filter(item -> item.getCode().equals("DIM_4e41a99d4b964cc0a66dd7c02356c473")).findFirst().orElseGet(() -> monthDimList.get(0));
            dateDimInfoMap.put("月", monthDimInfo);
        }


        // 年的维度信息
        BaseInfoDTO yearDimInfo = null;
        if (null != groupByCategory.get(5) && !groupByCategory.get(5).isEmpty()) {
            List<BaseInfoDTO> yearDimList = groupByCategory.get(5);
            yearDimInfo = yearDimList.stream().filter(item -> item.getCode().equals("DIM_0a61b0022ae241e7a400399e97dc1e63")).findFirst().orElseGet(() -> yearDimList.get(0));
            dateDimInfoMap.put("年", yearDimInfo);
        }

        // 日的维度信息
        BaseInfoDTO dayDimInfo = null;
        if (null != groupByCategory.get(1) && !groupByCategory.get(1).isEmpty()) {
            List<BaseInfoDTO> dayDimList = groupByCategory.get(1);
            dayDimInfo = dayDimList.stream().filter(item -> item.getCode().equals("DIM_a15f9bcd0235428fbaf164b584f8055f")).findFirst().orElseGet(() -> dayDimList.get(0));
            dateDimInfoMap.put("日", dayDimInfo);
        }

        // 如果没有时间类型的维度，直接返回
        if (dateDimInfoMap.isEmpty()) {
            return dataSource;
        }


        List<TimeNLP> timeNLPList = TimeNLPUtil.parse(word);

        List<Filter> filterInfoList = dataSource.getFilterList();
        List<String> filterDateList = new ArrayList<>();
        // 识别的时间字段

        String dateDim = "";
        String dateDimWord = "";
        SimpleDateFormat dateFormat = null;


        if (timeNLPList.size() >= 1) {

            int firstLeng = 0;
            int secondLeng = 0;
            if (timeNLPList.size() == 1) {
                firstLeng = timeNLPList.get(0).getTimeNorm().length();
                timeNLPList.add(timeNLPList.get(0));
                secondLeng = firstLeng;

                dataSource.getRemoveWordSet().add(findCommonSubstring(timeNLPList.get(0).getTimeExpression(), timeNLPList.get(0).getTimeNorm()));
            } else if (timeNLPList.size() == 2) {
                firstLeng = timeNLPList.get(0).getTimeNorm().length();
                secondLeng = timeNLPList.get(1).getTimeNorm().length();

                dataSource.getRemoveWordSet().add(findCommonSubstring(timeNLPList.get(0).getTimeExpression(), timeNLPList.get(0).getTimeNorm()));
                dataSource.getRemoveWordSet().add(findCommonSubstring(timeNLPList.get(1).getTimeExpression(), timeNLPList.get(1).getTimeNorm()));
            }

            int formatDateLeng = Math.max(firstLeng, secondLeng);


            if (formatDateLeng <= 5) {
                // 年
                if (null != dateDimInfoMap.get("年")) {
                    dateFormat = new SimpleDateFormat("yyyy");
                    dateDim = dateDimInfoMap.get("年").getCode();
                    dateDimWord = "年";
                }

            } else if (formatDateLeng >= 7 && formatDateLeng <= 8) {
                // 月
                if (null != dateDimInfoMap.get("月")) {
                    dateFormat = new SimpleDateFormat("yyyyMM");
                    dateDim = dateDimInfoMap.get("月").getCode();
                    dateDimWord = "月";
                }
            } else {
                // 日
                if (null != dateDimInfoMap.get("日")) {
                    dateFormat = new SimpleDateFormat("yyyy-MM-dd");
                    dateDim = dateDimInfoMap.get("日").getCode();
                    dateDimWord = "日";
                }
            }

            if (null != dateFormat) {
                if (timeNLPList.get(0).getTime().toInstant().isAfter(timeNLPList.get(1).getTime().toInstant())) {
                    filterDateList.add(dateFormat.format(timeNLPList.get(1).getTime()));
                    filterDateList.add(dateFormat.format(timeNLPList.get(0).getTime()));
                } else {
                    filterDateList.add(dateFormat.format(timeNLPList.get(0).getTime()));
                    filterDateList.add(dateFormat.format(timeNLPList.get(1).getTime()));
                }
            }

        } else {


            String currentDatePatter = "(最近|近)(半|半个|[" + CommonConstants.LARGE_NUMERALS + "]+|\\d+|[零壹贰叁肆伍陆柒捌玖]+)(\\S{2})";

            Pattern pattern = Pattern.compile(currentDatePatter);
            Matcher matcher = pattern.matcher(word);


            LocalDate now = LocalDate.now();
            LocalDate startTime = null;
            LocalDate endTime = null;
            Boolean dimFlag = true;

            int monthBefore = 11;
            int yearBefore = 1;
            int dayBefore = 14;
            Boolean isUserFormFlag = false;
            if (matcher.find() && matcher.group().length() >= 3 && (matcher.group(1).contains("最近") || matcher.group(1).contains("近"))) {


                // .add(matcher.group(2)).add(matcher.group(3)
                String lastStr = matcher.group(3);
                if (!lastStr.contains("个月")) {
                    lastStr = lastStr.substring(0, lastStr.length() - 1);
                }
                log.info("group split {}", matcher.group(1) + matcher.group(2) + lastStr);
                dataSource.getRemoveWordSet().add(matcher.group(1) + matcher.group(2) + lastStr);

                String limitStr = matcher.group(2);
                if (limitStr.contains("半")) {
                    if (matcher.group(3).contains("月")) {
                        monthBefore = getDateBeforeNumber("15");
                        if (null != dateDimInfoMap.get("日")) {
                            isUserFormFlag = true;
                            if (null != dateDimInfoMap.get("日")) {
                                formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd");
                                dateDim = dateDimInfoMap.get("日").getCode();
                                dateDimWord = "日";
                                startTime = now.minusDays(dayBefore - 1);
                                endTime = now;
                            }
                        }
                    } else if (matcher.group(3).contains("年")) {
                        yearBefore = getDateBeforeNumber("5");
                        isUserFormFlag = true;
                        if (null != dateDimInfoMap.get("月")) {
                            formatter = DateTimeFormatter.ofPattern("yyyyMM");
                            dateDim = dateDimInfoMap.get("月").getCode();
                            dateDimWord = "月";
                            startTime = now.minusMonths(yearBefore);
                            endTime = now.with(TemporalAdjusters.lastDayOfMonth());
                        }
                    }
                } else {
                    if (matcher.group(3).contains("月")) {
                        monthBefore = getDateBeforeNumber(matcher.group(2));
                        if (null != dateDimInfoMap.get("月")) {
                            isUserFormFlag = true;
                            formatter = DateTimeFormatter.ofPattern("yyyyMM");
                            dateDim = dateDimInfoMap.get("月").getCode();
                            dateDimWord = "月";

                            // 本月前12个月
                            startTime = now.minusMonths(monthBefore - 1);
                            // 本月的最后一天
                            endTime = now.with(TemporalAdjusters.lastDayOfMonth());
                        }
                    } else if (matcher.group(3).contains("年")) {
                        yearBefore = getDateBeforeNumber(matcher.group(2));
                        isUserFormFlag = true;
                        if (null != dateDimInfoMap.get("年")) {
                            formatter = DateTimeFormatter.ofPattern("yyyy");
                            dateDim = dateDimInfoMap.get("年").getCode();
                            dateDimWord = "年";
                            startTime = now.minusYears(yearBefore);
                            endTime = now.withYear(now.getYear());
                        }
                    } else if (matcher.group(3).contains("天") || matcher.group(3).contains("日")) {
                        dayBefore = getDateBeforeNumber(matcher.group(2));
                        isUserFormFlag = true;
                        if (null != dateDimInfoMap.get("日")) {
                            formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd");
                            dateDim = dateDimInfoMap.get("日").getCode();
                            dateDimWord = "日";
                            startTime = now.minusDays(dayBefore - 1);
                            endTime = now;
                        }
                    }
                }
            } else {


                // 如果没有识别到时间，默认取当前时间 到 上一年的时间 数据

                dataSource.setNatureTimeDefault(false);
                if (null != dateDimInfoMap.get("月")) {

                    formatter = DateTimeFormatter.ofPattern("yyyyMM");
                    dateDim = dateDimInfoMap.get("月").getCode();
                    dateDimWord = "月";
                    // 本月前12个月
                    startTime = now.minusMonths(monthBefore);
                    // 本月的最后一天
                    endTime = now.with(TemporalAdjusters.lastDayOfMonth());
                    dimFlag = false;
                }

                if (null != dateDimInfoMap.get("年") && dimFlag) {
                    formatter = DateTimeFormatter.ofPattern("yyyy");
                    dateDim = dateDimInfoMap.get("年").getCode();
                    dateDimWord = "年";
                    startTime = now.minusYears(yearBefore);
                    endTime = now.withYear(now.getYear());
                    dimFlag = false;
                }

                if (null != dateDimInfoMap.get("日") && dimFlag) {
                    formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd");
                    dateDim = dateDimInfoMap.get("日").getCode();
                    dateDimWord = "日";
                    startTime = now.minusDays(dayBefore);
                    endTime = now;
                }
            }


            if (null != startTime && null != endTime) {
                String startDate = LocalDateTime.of(startTime, LocalTime.MIN).format(formatter);
                String endDate = LocalDateTime.of(endTime, LocalTime.MAX).format(formatter);
                filterDateList.add(startDate);
                filterDateList.add(endDate);
            }

        }

        if (!Objects.equals(dateDim, "")) {

            Filter filter = new Filter();
            // filter.setName();
            // Dimension dimensionInfo = indicatorService.getDimensionTableInfo(dateDim);
            filter.setCode(dateDim);

            if (null != CommonConstants.DateDimCodeNameMap.get(dateDim)) {
                filter.setName(CommonConstants.DateDimCodeNameMap.get(dateDim));
            } else {
                Boolean defaultDateNameFlag = true;
                for (BaseInfoDTO baseInfoDTO : dateList) {
                    if (Objects.equals(baseInfoDTO.getCode(), dateDim)) {
                        defaultDateNameFlag = false;
                        filter.setName(baseInfoDTO.getCnName());
                        ViewType viewType = ViewType.findByInt(baseInfoDTO.getViewType()).orElse(null);
                        filter.setViewType(viewType);
                        break;
                    }
                }
                if (defaultDateNameFlag) {
                    filter.setName("日期");
                }

            }

            Operator operator = new Operator();
            operator.setSqlOprType(SqlOprType.BETEEN);
            operator.setTimeRange(TimeRange.DATE);
            operator.setDataList(filterDateList);

            filter.setOperatorList(Collections.singletonList(operator));
            filterInfoList.add(filter);

            relatedCodeSet.getDimensionSet().add(dateDim);
        }

        BaseConfigure baseConfigure = new BaseConfigure();
        // 默认设置上，条件识别到的维度
        if (!Objects.equals(dateDim, "") && filterDateList.size() > 1 && !Objects.equals(filterDateList.get(0), filterDateList.get(1))) {
            // 默认降序
            Order order = new Order();
            order.setSortType(SortType.DESC);
            baseConfigure.setOrder(order);
            baseConfigure.setCode(dateDim);
            baseConfigure.setCodeAlias(dateDim);
            baseConfigure.setWordName(dateDimWord);
        }
        for (Term termItem : splitList) {

            // 日 周 月 季 年 顺序识别
            if (null != CommonConstants.DateKeyMap.get(termItem.getName())) {

                if (null != dateDimInfoMap.get(CommonConstants.DateKeyMap.get(termItem.getName()))) {
                    baseConfigure.setCode(dateDimInfoMap.get(CommonConstants.DateKeyMap.get(termItem.getName())).getCode());
                    baseConfigure.setCodeAlias(dateDimInfoMap.get(CommonConstants.DateKeyMap.get(termItem.getName())).getCode());
                    baseConfigure.setWordName(CommonConstants.DateKeyMap.get(termItem.getName()));
                    Order order = new Order();
                    order.setSortType(SortType.DESC);
                    baseConfigure.setOrder(order);
                    relatedCodeSet.getDimensionSet().add(dateDimInfoMap.get(CommonConstants.DateKeyMap.get(termItem.getName())).getCode());
                    dataSource.getRemoveWordSet().add(termItem.getName());
                    dataSource.setDateExit(true);
                    break;
                }
            }
        }

        if (null != baseConfigure.getCode() && !Objects.equals(baseConfigure.getCode(), "")) {
            dataSource.getConfigureList().add(baseConfigure);
            dataSource.setSingle(false);
        } else {
            // 如果是单个指标，设置同环比
            if (dataSource.isSingle()) {
                getRatioConfig(dataSource);
            }
        }


        return dataSource;
    }

    private DataSource getRatioConfig(DataSource dataSource) {


        // 单个指标设置同比环比
        List<Ratio> ratioList = new ArrayList<>();
        Map<String, Integer> mapSettings = new HashMap<>();
        mapSettings.put("type", 5);
        String typeString = com.alibaba.fastjson.JSON.toJSONString(mapSettings);

        Ratio ratio = new Ratio();
        ratio.setRatioType(RatioType.MONTHONMONTH);
        ratio.setRatioExpType(RatioExpType.DIFFPERCENTAGE);
        ratio.setSettings(typeString);
        ratioList.add(ratio);

        Ratio ratioYear = new Ratio();
        ratioYear.setRatioType(RatioType.YEARYEMOM);
        ratioYear.setRatioExpType(RatioExpType.DIFFPERCENTAGE);
        ratioYear.setSettings(typeString);
        ratioList.add(ratioYear);
        dataSource.getConfigureList().get(0).setRatioList(ratioList);


        if (!dataSource.getFilterList().isEmpty()) {

            Map<String, Object> stringObjectMap = getRatioRangeTimeConfig(dataSource);
            dataSource.setRatioRangeTime(stringObjectMap);
            BaseConfigure baseConfigureSingle = new BaseConfigure();
            baseConfigureSingle.setCode(dataSource.getFilterList().get(0).getCode());
            baseConfigureSingle.setName(dataSource.getFilterList().get(0).getName());
            dataSource.getConfigureList().add(baseConfigureSingle);
        }
        return dataSource;
    }

    private Map<String, Object> getRatioRangeTimeConfig(DataSource dataSource) {
        List<Filter> filterInfoList = dataSource.getFilterList();
        Map<String, Object> stringObjectMap = new HashMap<>();
        if (!filterInfoList.isEmpty()) {
            String endStr = null;
            String startStr = null;
            DateTimeFormatter inputFormatter = null;
            DateTimeFormatter outputFormatter = null;
            BaseConfigure baseConfigureSingle = new BaseConfigure();
            List<Operator> operatorListItem = filterInfoList.get(0).getOperatorList();
            if (!operatorListItem.isEmpty() && operatorListItem.get(0).getDataList().size() == 2) {
                String endDate = operatorListItem.get(0).getDataList().get(0);

                switch (endDate.length()) {
                    case 4:
                        inputFormatter = DateTimeFormatter.ofPattern("yyyy");
                        Year year = Year.parse(endDate, inputFormatter);
                        Year beforeYear = year.minusYears(1);
                        outputFormatter = DateTimeFormatter.ofPattern("yyyy");
                        endStr = year.format(outputFormatter);
                        startStr = beforeYear.format(outputFormatter);
                        break;
                    case 6:
                        inputFormatter = DateTimeFormatter.ofPattern("yyyyMM");
                        YearMonth yearMonth = YearMonth.parse(endDate, inputFormatter);
                        YearMonth beforeMonth = yearMonth.minusMonths(1);
                        outputFormatter = DateTimeFormatter.ofPattern("yyyy-MM");
                        endStr = yearMonth.format(outputFormatter);
                        startStr = beforeMonth.format(outputFormatter);
                        break;
                    case 10:
                        inputFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd");
                        LocalDate date = LocalDate.parse(endDate, inputFormatter);
                        LocalDateTime dateTime = date.atStartOfDay();
                        LocalDateTime beforeDay = dateTime.minusDays(1);
                        endStr = endDate;
                        startStr = beforeDay.format(inputFormatter);
                        break;
                }

            }

            stringObjectMap.put("startTimeRatio", startStr);
            stringObjectMap.put("endTimeRatio", endStr);


        }
        return stringObjectMap;
    }


    private int getDateBeforeNumber(String numberStr) {
        if (Objects.equals(numberStr, "半")) {
            numberStr = "5";
        }
        RuleBasedNumberFormat rbnf = new RuleBasedNumberFormat(Locale.CHINA, RuleBasedNumberFormat.SPELLOUT);

        try {
            Number number = rbnf.parse(numberStr);
            log.info("阿拉伯数字：" + number.toString());
            return number.intValue();
        } catch (ParseException e) {
            e.printStackTrace();
            return 0;
        }
    }

    public DataSource getMeasureSource(List<Term> splitList, DataSource dataSource, RelatedCodeSet relatedCodeSet, RelatedSet relatedSet) {

        List<Measure> measureList = this.indicatorService.listAllMeasure();
        Map<String, Measure> measureMap = measureList.stream().collect(Collectors.toMap(measure -> measure.getName().toLowerCase(), Function.identity(), (re, ex) -> re));

        List<BaseConfigure> baseConfigures = dataSource.getConfigureList();

        List<String> measureNameList = new ArrayList<>();
        for (Term term : splitList) {

            if (null == measureMap.get(term.getName())) {
                continue;
            }
            if (dataSource.getRemoveWordSet().contains(term.getName())) {
                dataSource.getRemoveWordSet().add(term.getName());
                continue;
            }
            BaseConfigure baseConfigure = new BaseConfigure();


            baseConfigure.setWordName(term.getName());
            baseConfigure.setCode(measureMap.get(term.getName()).getCode());
            baseConfigure.setCodeAlias(measureMap.get(term.getName()).getCode());
            // 默认降序
            Order order = new Order();
            order.setSortType(SortType.DEFAULT);
            baseConfigure.setOrder(order);
            baseConfigures.add(baseConfigure);

            measureNameList.add(term.getName());

            relatedCodeSet.getMeasureSet().add(measureMap.get(term.getName()).getCode());
            relatedSet.getMeasureSet().add(measureMap.get(term.getName()).getId().intValue());
            dataSource.getRemoveWordSet().add(term.getName());
        }
        for (Term term : splitList) {
            // todo 如果包含 最高 等字段，增加排序
            if (null != CommonConstants.SortMap.get(term.getName())) {
                dataSource.getRemoveWordSet().add(term.getName());

                dataSource.getConfigureList().forEach(config -> {
                    Order order = new Order();
                    if (Objects.equals(term.getName(), "最高")) {
                        order.setSortType(SortType.DESC);
                    } else if (Objects.equals(term.getName(), "最低")) {
                        order.setSortType(SortType.ASC);
                    } else {
                        order.setSortType(SortType.DESC);
                    }
                    config.setOrder(order);
                });
                break;
            }
        }
        // 单个指标设置同比环比
        if (baseConfigures.size() == 1) {
            dataSource.setSingle(true);
        }

        dataSource.setConfigureList(baseConfigures);

        return dataSource;
    }

    private List<String> removeSplitWord(Set<String> removeList, List<String> splitList) {
        if (removeList.isEmpty()) {
            return splitList;
        }
        return splitList.stream().filter(s -> !removeList.contains(s)).collect(Collectors.toList());

    }

    private List<Term> removeSplitTerm(Set<String> removeList, List<Term> splitList) {
        if (removeList.isEmpty()) {
            return splitList;
        }
        return splitList.stream().filter(s -> !removeList.contains(s.getName())).collect(Collectors.toList());

    }


    @Autowired
    private DimAllValuesMapper dimAllValuesMapper;


    private static List<Value> VALUSE_LIST = new ArrayList<Value>();

    private static Forest FOREST = null;

    private static List<Value> VALUSE_LIST_IFNO = new ArrayList<Value>();
    private static Forest FOREST_INFO = null;

    private List<Term> split(String word) {
        // 文字替换

        // 使用日期分词，将识别到的分词也作为字典
        List<TimeNLP> timeNLPList = TimeNLPUtil.parse(word);

        timeNLPList.forEach(timeNLP -> {
            if (Objects.equals(timeNLP.getTimeExpression(), timeNLP.getTimeNorm())) {
                log.info("时间分词 {} {}", timeNLP.getTimeExpression(), timeNLP.getTimeNorm());
                VALUSE_LIST_IFNO.add(new Value(timeNLP.getTimeExpression(), "kw", "2000"));
            } else {
                String commonPrefix = findCommonSubstring(timeNLP.getTimeExpression(), timeNLP.getTimeNorm());
                VALUSE_LIST_IFNO.add(new Value(commonPrefix, "kw", "2000"));
            }
        });


        String currentDatePatter = "(最近|近)(半|[" + CommonConstants.LARGE_NUMERALS + "]+|\\d+|[零壹贰叁肆伍陆柒捌玖]+)(\\S{2})";
        Pattern pattern = Pattern.compile(currentDatePatter);
        Matcher matcher = pattern.matcher(word);

        if (matcher.find() && matcher.group().length() >= 3 && (matcher.group(1).contains("最近") || matcher.group(1).contains("近"))) {
            log.info("check is {} ", matcher.group(1) + matcher.group(2) + matcher.group(3));
            String lastStr = matcher.group(3);
            if (!lastStr.contains("个月") && lastStr.length() > 1) {
                lastStr = lastStr.substring(0, lastStr.length() - 1);
            }
            //VALUSE_LIST.add(new Value(matcher.group(1) + matcher.group(2) + lastStr, "kw", "1000"));
            VALUSE_LIST_IFNO.add(new Value(matcher.group(1) + matcher.group(2) + lastStr, "kw", "1000"));
        }

        // FOREST = Library.makeForest(VALUSE_LIST);

        if (!VALUSE_LIST_IFNO.isEmpty()) {
            if (FOREST == null) {
                log.info("init forest");
                FOREST = Library.makeForest(VALUSE_LIST_IFNO);
            } else {
                Iterator var2 = VALUSE_LIST_IFNO.iterator();

                while (var2.hasNext()) {
                    Value value = (Value) var2.next();
                    Library.insertWord(FOREST, value.toString());
                }
            }

        }

        Result result = DicAnalysis.parse(word, FOREST);

        List<Term> termList = result.getTerms();


        return termList;

    }

    @Autowired
    private ContentValuesMapper contentValuesMapper;

    @Autowired
    private DimensionValuesMapper dimensionValuesMapper;

    // @PostConstruct
    public void init() {
        // 全量指标
        List<Measure> measureList = this.indicatorService.listAllMeasure();
        for (Measure measure : measureList) {
            //自定义词、词性。此处指标、维度、维度值都定义成名词。
            Value v = new Value(measure.getName().toLowerCase(), "kw", "1000");
            VALUSE_LIST.add(v);
        }

        // 全量维度
        List<Dimension> dimensionList = this.indicatorService.listAllDimension();
        for (Dimension dim : dimensionList) {
            Value dimV = new Value(dim.getName().toLowerCase(), "kw", "1000");
            VALUSE_LIST.add(dimV);
        }
        // 全量维度值
        List<DimensionValues> dimensionValueList = dimensionValuesMapper.selectList(null);
        for (DimensionValues dimVItem : dimensionValueList) {
            Value dimV = new Value(dimVItem.getVValue().toLowerCase(), "kw", "1000");
            VALUSE_LIST.add(dimV);
        }

        // 全量维度值
        List<DimAllValuesInfo> dimensionAllValueList = dimAllValuesMapper.selectAllDimList();
        for (DimAllValuesInfo dimVItem : dimensionAllValueList) {
            Value dimV = new Value(dimVItem.getValueText().toLowerCase(), "kw", "2000");
            VALUSE_LIST.add(dimV);
        }
        // 日期字典
        CommonConstants.DateKeyMap.forEach((datek, dateV) -> {
            VALUSE_LIST.add(new Value(datek, "n", "1000"));
        });
        // 车型字典
        CommonConstants.CarCodeMap.forEach((carK, carV) -> {
            VALUSE_LIST.add(new Value(carK, "n", "1000"));
        });

        wordValuesMapper.selectInfoList().forEach(wordValues -> {
            VALUSE_LIST.add(new Value(wordValues.getValue(), "kw", "1000"));
            VALUSE_LIST.add(new Value(wordValues.getKey(), "kw", "1000"));
        });
        // 句式判断词初始化
        contentValuesMapper.selectList(null).forEach(contentValues -> {
            VALUSE_LIST.add(new Value(contentValues.getRuleText(), "kw", "1000"));
        });

        FOREST = Library.makeForest(VALUSE_LIST);
        DicAnalysis.parse("测试", FOREST);
    }

    // 获取最大子字串
    public String findCommonSubstring(String s1, String s2) {
        if (s1 == null || s2 == null || s1.length() == 0 || s2.length() == 0) {
            return "";
        }

        int maxLen = 0;
        int endIndex = 0;

        int[][] table = new int[s1.length()][s2.length()];

        for (int i = 0; i < s1.length(); i++) {
            for (int j = 0; j < s2.length(); j++) {
                if (s1.charAt(i) == s2.charAt(j)) {
                    if (i == 0 || j == 0) {
                        table[i][j] = 1;
                    } else {
                        table[i][j] = table[i - 1][j - 1] + 1;
                    }
                    if (table[i][j] > maxLen) {
                        maxLen = table[i][j];
                        endIndex = i;
                    }
                }
            }
        }

        return s1.substring(endIndex - maxLen + 1, endIndex + 1);
    }
//
//    private List<Term> splitSimple(String word) {
//
//    }


    private List<Term> splitInfo(String word, List<Measure> measureList, List<Dimension> dimensionList, List<String> timeList) {
        // 文字替换


        if (FOREST_INFO == null) {

            // 全量指标
            for (Measure measure : measureList) {
                //自定义词、词性。此处指标、维度、维度值都定义成名词。
                Value v = new Value(measure.getName().toLowerCase(), "kw", "1000");
                VALUSE_LIST.add(v);
            }

            // 全量维度
            for (Dimension dim : dimensionList) {
                Value dimV = new Value(dim.getName().toLowerCase(), "kw", "1000");
                VALUSE_LIST.add(dimV);
            }


            // 日期字典
            CommonConstants.DateKeyMap.forEach((datek, dateV) -> {
                VALUSE_LIST.add(new Value(datek, "n", "1000"));
            });
            // 车型字典
            CommonConstants.CarCodeMap.forEach((carK, carV) -> {
                VALUSE_LIST.add(new Value(carK, "n", "1000"));
            });

            wordValuesMapper.selectInfoList().forEach(wordValues -> {
                VALUSE_LIST.add(new Value(wordValues.getValue(), "kw", "1000"));
                VALUSE_LIST.add(new Value(wordValues.getKey(), "kw", "1000"));
            });

        }
        if (timeList.isEmpty()) {
            if (null == FOREST_INFO) {
                FOREST_INFO = Library.makeForest(VALUSE_LIST);
            }
        } else {
            timeList.forEach(v -> {
                VALUSE_LIST.add(new Value(v, "kw", "2000"));
            });
            FOREST_INFO = Library.makeForest(VALUSE_LIST);
        }


        Result result = DicAnalysis.parse(word, FOREST_INFO);
        List<Term> termList = result.getTerms();


        return termList;

    }

    List<String> getTimeSplit(String word) {
        List<String> stringList = new ArrayList<>();
        String currentDatePatter = "(最近|近)(半|[" + CommonConstants.LARGE_NUMERALS + "]+|\\d+|[零壹贰叁肆伍陆柒捌玖]+)(\\S{2})";
        Pattern pattern = Pattern.compile(currentDatePatter);
        Matcher matcher = pattern.matcher(word);

        if (matcher.find() && matcher.group().length() >= 3 && matcher.group(1).contains("最近")) {
            log.info("check is {} ", matcher.group(1) + matcher.group(2) + matcher.group(3));
            String lastStr = matcher.group(3);
            if (!lastStr.contains("个月") && lastStr.length() > 1) {
                lastStr = lastStr.substring(0, lastStr.length() - 1);
            }
            stringList.add(matcher.group(1) + matcher.group(2) + lastStr);
        }

        // 使用日期分词，将识别到的分词也作为字典
        List<TimeNLP> timeNLPList = TimeNLPUtil.parse(word);
        timeNLPList.forEach(timeNLP -> {
            if (Objects.equals(timeNLP.getTimeExpression(), timeNLP.getTimeNorm())) {
                log.info("时间分词 {} {}", timeNLP.getTimeExpression(), timeNLP.getTimeNorm());
                stringList.add(timeNLP.getTimeExpression());

            } else {
                String commonPrefix = findCommonSubstring(timeNLP.getTimeExpression(), timeNLP.getTimeNorm());
                stringList.add(commonPrefix);
            }
        });
        return stringList;
    }

    @Autowired
    private ColumnsMapper columnsMapper;

    public List<Columns> listDetailTableColumns(String measCode) {
        List<Columns> columnsList = new ArrayList<>();
        MetadataCache metadataCache = cacheManager.getMetadataCache();
        Map<String, com.graphinsight.indicator.auto.entity.Measure> allMeasureCodeMap = metadataCache.getAllMeasureCodeMap();
        com.graphinsight.indicator.auto.entity.Measure measure = allMeasureCodeMap.get(measCode);
        MeasureCache measureCache = cacheManager.getMeasureCache(measure.getId());
        if (measureCache == null) {
            return columnsList;
        }
        List<Integer> detailDwTableIds = measureCache.getDetailDwTableIds();
        if (CollectionUtils.isEmpty(detailDwTableIds)) {
            return columnsList;
        }

        Integer tableId = detailDwTableIds.get(0);
        DwTableCache dwTableCache = cacheManager.getDwTableCache(tableId);
        if (dwTableCache == null) {
            return columnsList;
        }

        columnsList = columnsMapper.selectList(
                Wrappers.<Columns>lambdaQuery()
                        .eq(Columns::getTableSchema, dwTableCache.getDwTable().getSchemaName())
                        .eq(Columns::getTableName, dwTableCache.getDwTable().getTableName())
                        .orderByAsc(Columns::getColumnName));

        return columnsList;
    }


}
