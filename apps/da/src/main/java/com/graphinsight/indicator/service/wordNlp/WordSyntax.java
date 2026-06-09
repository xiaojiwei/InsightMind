package com.graphinsight.indicator.service.wordNlp;


import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.graphinsight.indicator.auto.mapper.DimensionMapper;
import com.graphinsight.indicator.auto.service.ITSpaceService;
import com.graphinsight.indicator.enums.*;
import com.graphinsight.indicator.manager.CacheManager;
import com.graphinsight.indicator.manager.UserManager;
import com.graphinsight.indicator.model.*;
import com.graphinsight.indicator.model.cache.DimensionCache;
import com.graphinsight.indicator.model.cache.MeasureCache;
import com.graphinsight.indicator.model.cache.MetadataCache;
import com.graphinsight.indicator.model.dto.UserContext;
import com.graphinsight.indicator.model.vo.AiCalculateVo;
import com.graphinsight.indicator.model.vo.AiFrontFormatVo;
import com.graphinsight.indicator.model.vo.WordSyntaxVo;
import com.graphinsight.indicator.util.StringUtil;
import com.graphinsight.indicator.util.UserThreadLocalUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.Year;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

/**
 *
 */
@Slf4j
@Service
public class WordSyntax {

    @Value("${meas.fei_yong_code:MEAS_b5159de555304d21b7869ac3c834b380}")
    private String feiYongCode;

    @Value("${dim.fei_yong_category_code:DIM_e2c424a6997343a19fcaf6371883436d}")
    private String feiYongCategoryCode;

    @Value("${dim.fei_yong_category_value:ACTUAL}")
    private String feiYongCategoryValue;

    @Value("${dim.fei_yong_category_value_desc:实际数}")
    private String feiYongCategoryValueDesc;


    @Value("${dim.fei_yong_account_code:DIM_c419804bc46b406abef945955a5c56d6}")
    private String feiYongAccountCode;

    @Value("${dim.fei_yong_category_value:EXP_DALL}")
    private String feiYongAccountValue;

    @Value("${dim.fei_yong_category_value_desc:交付费用合计}")
    private String feiYongAccountValueDesc;
    @Value("${dim.fei_yong_account_code_list:DIM_1bed611f72e649e6a35b7cfe78d9374d,DIM_c419804bc46b406abef945955a5c56d6,DIM_a35f4682c638450e867a3036f9441448,DIM_f73e6930139e491b973c70e3ddfd6556,DIM_ad0db5b66720417d8fadf8c4e35a827b,DIM_e4f67bbf3f1d427aab9548c65a52e93c}")
    private List<String> feiYongAccountCodeList;


    @Autowired
    WordChainFactory wordChainFactory;

    @Autowired
    CacheManager cacheManager;

    @Autowired
    private UserManager userManager;

    @Autowired
    private DimensionMapper dimensionMapper;

    public WordSyntaxVo splitInfo(String wordText) {

        // 句式识别以及替换
        WordSyntaxVo wordSyntaxVo = new WordSyntaxVo();
        // 最原始的文本
        wordSyntaxVo.setRootText(wordText);

        wordChainFactory.wordChain().handleProcess(wordSyntaxVo);

        // 构建dataSource
//        buildDataSource(wordSyntaxVo);
//        DataSource dataSource = buildDataSource(wordSyntaxVo);
        return wordSyntaxVo;
    }

    public DataSource queryNlp(WordSyntaxVo wordSyntaxVo) {
        DataSource dataSource = new DataSource();
        if (wordSyntaxVo.getMeasNum() == null) {

        }
        return dataSource;
    }

    public DataSource buildDataSource(WordSyntaxVo wordSyntaxVo) {
        DataSource dataSource = new DataSource();

        Integer singleInt = 0;

        for (WordSyntaxVo.WordItem wordItem : wordSyntaxVo.getWordItemList()) {
            if (wordItem.getMatchText().contains("同比")
                    || wordItem.getMatchText().contains("环比")
                    || wordItem.getMatchText().contains("同环比")
                    || wordItem.getMatchText().contains("环同比")
            ) {
                dataSource.setShowRatio(true);
            }
            if (wordItem.getBoolSet().isEmpty() && !Objects.equals(wordItem.getSqlType(), "order")) {
                continue;
            }

            BaseConfigure baseConfigure = new BaseConfigure();
            if (Objects.equals(wordItem.getMatchType(), "measure")) {
                MeasureCache measure = cacheManager.getMeasureCache(wordItem.getBoolSet().stream().findFirst().get());

                if (Objects.equals(measure.getCode(), feiYongCode)) {
                    dataSource.setMeasDealType("FEI_YONG");
                }
                baseConfigure.setCode(measure.getCode());
                baseConfigure.setId(Long.valueOf(measure.getId()));
                baseConfigure.setName(measure.getMeasure().getCnName());
                if (null != measure.getMeasure().getFunctionType()) {
                    switch (measure.getMeasure().getFunctionType()) {
                        case "CPD":
                            baseConfigure.setExpression("CDP([" + feiYongCode + "])");
                            break;
                        case "ER":
                            baseConfigure.setExpression("ER([" + feiYongCode + "])");
                            break;
                    }
                }

                baseConfigure.setWordName(wordItem.getMatchText());
                if (null != dataSource.getMeasConfMap().get(measure.getCode())) {
                    continue;
                }
                if (null != wordItem.getOrderType()) {
                    Order order = new Order();
                    dataSource.setExitOrder(true);
                    if (wordItem.getOrderType().equals("asc")) {
                        order.setSortType(SortType.ASC);
                    } else {
                        order.setSortType(SortType.DESC);
                    }
                    baseConfigure.setOrder(order);
                }
                if (!measure.getMeasureApplicationCacheList().isEmpty()) {
                    AiFrontFormatVo aiFrontFormatVo = new AiFrontFormatVo();
                    DataFormatType dataFormatType = DataFormatType.findNullableByString(measure.getMeasureApplicationCacheList().get(0).getDataFormatStr());
                    aiFrontFormatVo.setType(dataFormatType.getCode());
                    aiFrontFormatVo.setDecimalPlaces(measure.getMeasureApplicationCacheList().get(0).getDecimalPlaces());
                    aiFrontFormatVo.setDataScale(DecimalFormatType.findNullableByCode(measure.getMeasureApplicationCacheList().get(0).getDataScale()));
                    baseConfigure.setFormat(aiFrontFormatVo);
                }

                dataSource.getMeasConfMap().put(wordItem.getMatchText(), baseConfigure);

                if (Objects.equals(wordItem.getWordType(), "singleOp")) {
                    if (null != SqlOprType.getTypeByDesc(wordItem.getValueType())) {
                        Filter filterMeas = new Filter();
                        Operator operatorMeas = new Operator();
                        operatorMeas.setSqlOprType(SqlOprType.getTypeByDesc(wordItem.getValueType()));
                        filterMeas.setCode(measure.getCode());
                        filterMeas.setName(measure.getMeasure().getCnName());
                        filterMeas.setWordName(wordItem.getMatchText());
                        filterMeas.setId(Long.valueOf(measure.getId()));
                        operatorMeas.getDataList().addAll(wordItem.getValueList());
                        filterMeas.getOperatorList().add(operatorMeas);
                        dataSource.getFilterMap().put(wordItem.getMatchText(), filterMeas);
                    }
                }
                singleInt++;
                if (singleInt == 1) {
                    dataSource.setSingle(true);
                } else {
                    dataSource.setSingle(false);
                }
            } else {
                if (wordItem.getSqlType() == null) {
                    continue;
                }
                // 如果是group 构建group
                wordItem.getBoolSet().retainAll(wordSyntaxVo.getRelatedSet().getDimensionSet());
                dataSource.getBoolValidSet().addAll(wordItem.getBoolSet());
                DimensionCache dimensionCache = cacheManager.getDimensionCache(wordItem.getBoolSet().stream().findFirst().orElse(null));
                switch (wordItem.getSqlType()) {
                    case "where":
                    case "numeratorWhere":
                    case "denominatorWhere":
                        List<String> valueTextList = new ArrayList<>();
                        Filter filter = new Filter();
                        Operator operator = new Operator();
                        if (Objects.equals(wordItem.getMatchType(), "date")) {
                            dataSource.setIsDimDateFilter(true);
                            // 处理是否是 自然日期
                            dimensionCache = getDateDimInfo(wordItem.getWordType(), wordItem.getBoolSet());
                            if (Objects.equals(wordItem.getValueType(), "rangeIn")) {
                                operator.setSqlOprType(SqlOprType.IN);
                            } else {
                                operator.setSqlOprType(SqlOprType.BETEEN);
                            }
                            operator.setTimeRange(TimeRange.DATE);
                        } else if (Objects.equals(wordItem.getMatchType(), "dimValue")) {
                            if (!wordItem.getValueList().isEmpty()) {
                                wordItem.getBoolSet().retainAll(wordSyntaxVo.getRelatedSet().getDimensionSet());
                                if (!wordItem.getBoolSet().isEmpty()) {
                                    Integer id = wordItem.getBoolSet().stream().findFirst().orElse(null);
                                    List<String> valueList = new ArrayList<>();

                                    wordItem.getValueList().forEach(val -> {
                                        List<String> valList = Arrays.asList(val.split("~"));
                                        if (Integer.valueOf(valList.get(1)).equals(id)) {
                                            valueList.add(valList.get(0));
                                            valueTextList.add(valList.get(2));
                                        }
                                    });
                                    wordItem.setValueList(valueList);
                                }
                            }
                            operator.setSqlOprType(SqlOprType.IN);
                            if (SqlOprType.getTypeByDesc(wordItem.getValueType()) == SqlOprType.NOTIN) {
                                operator.setSqlOprType(SqlOprType.NOTIN);
                            }
                        }
                        if (dimensionCache == null) {
                            break;
                        }
                        filter.setCode(dimensionCache.getCode());
                        filter.setName(dimensionCache.getCnName());
                        filter.setWordName(wordItem.getMatchText());
                        filter.setViewType(ViewType.findByInt(dimensionCache.getDimension().getViewType()).orElse(null));
                        filter.setId(Long.valueOf(dimensionCache.getId()));
                        // 同环比不支持时间
                        if (dataSource.isShowRatio()) {
                            operator.getDataList().add(wordItem.getValueList().get(0));
                            operator.getDataList().add(wordItem.getValueList().get(1));
                        } else {
                            operator.getDataList().addAll(wordItem.getValueList());
                            if (!valueTextList.isEmpty()) {
                                operator.getDataValueList().addAll(valueTextList);
                            }
                        }

                        filter.getOperatorList().add(operator);
                        dataSource.getFilterMap().put(filter.getWordName(), filter);
                        break;

                    case "group":
                        if (Objects.equals(wordItem.getMatchType(), "date")) {
                            dimensionCache = getDateDimInfo(wordItem.getWordType(), wordItem.getBoolSet());
                            dataSource.setIsDimDate(true);
                        }
                        if (null != wordItem.getOrderType()) {
                            dataSource.setExitOrder(true);
                            Order order = new Order();
                            if (wordItem.getOrderType().equals("asc")) {
                                order.setSortType(SortType.ASC);
                            } else {
                                order.setSortType(SortType.DESC);
                            }
                            baseConfigure.setOrder(order);
                        }
                        if (dimensionCache == null) {
                            break;
                        }
                        baseConfigure.setViewType(ViewType.findByInt(dimensionCache.getDimension().getViewType()).orElse(null));
                        baseConfigure.setCode(dimensionCache.getCode());
                        baseConfigure.setName(dimensionCache.getCnName());
                        baseConfigure.setWordName(wordItem.getMatchText());
                        baseConfigure.setId(Long.valueOf(dimensionCache.getId()));
                        if (null != dataSource.getDimConfMap().get(dimensionCache.getCode())) {
                            continue;
                        }
                        dataSource.getDimConfMap().put(baseConfigure.getCode(), baseConfigure);
                        break;
                    case "order":
                        if (!wordItem.getValueList().isEmpty()) {
                            dataSource.setLimitNum(Integer.parseInt(wordItem.getValueList().get(0)));
                        }
                        dataSource.setOrderType(wordItem.getValueType());
                        break;
                }

            }

        }
        if (dataSource.getBoolSet().isEmpty()) {
            dataSource.getBoolSet().addAll(wordSyntaxVo.getRelatedSet().getDimensionSet());
        }
        if (dataSource.getBoolValidSet().isEmpty()) {
            dataSource.getBoolValidSet().addAll(wordSyntaxVo.getRelatedSet().getDimensionSet());
        }
        // 构建表达式
        buildCalculate(dataSource, wordSyntaxVo);

        buildSourceInfo(dataSource, wordSyntaxVo);

        // 剔除无效的配置

        if (dataSource.getConfigureList().isEmpty() && dataSource.getFilterList().isEmpty() && dataSource.getNoDataRangeList().isEmpty()) {
            return null;
        }

        // 设置默认时间范围
        buildDefaultFilter(dataSource);

        // 设置同环比
        buildRatio(dataSource, wordSyntaxVo);
        // 设置默认排序
        buildDefaultOrder(dataSource);

        // 设置无权限内容，未识别内容

        return dataSource;
    }

    DataSource buildSourceInfo(DataSource dataSource, WordSyntaxVo wordSyntaxVo) {

        Map<String, BaseConfigure> configureCodeMap = new LinkedHashMap<>();
        List<String> removeMeas = new ArrayList<>();
        List<String> removeFilters = new ArrayList<>();
        if (wordSyntaxVo.getNumerator().getUniqueKey() != null && wordSyntaxVo.getDenominator().getUniqueKey() != null) {
            removeMeas.addAll(Arrays.asList(wordSyntaxVo.getNumerator().getUniqueKey().split("~")));
            removeMeas.addAll(Arrays.asList(wordSyntaxVo.getDenominator().getUniqueKey().split("~")));
            removeFilters = wordSyntaxVo.getNumerator().getSonTextList();
            removeFilters.addAll(wordSyntaxVo.getDenominator().getSonTextList());
        }


        List<String> opDataList = new ArrayList<>();
        Map<String, Filter> filterCodeMap = new LinkedHashMap<>();
        Iterator<Map.Entry<String, Filter>> iteratorFilter = dataSource.getFilterMap().entrySet().iterator();
        while (iteratorFilter.hasNext()) {
            Map.Entry<String, Filter> entry = iteratorFilter.next();
            if (removeFilters.contains(entry.getKey())) {
                iteratorFilter.remove(); // 使用迭代器的remove方法删除当前元素
            } else {
                // 合并相同维度的值
                Filter filter = entry.getValue();
                opDataList.addAll(filter.getOperatorList().get(0).getDataList());
                if (null != filterCodeMap.get(filter.getCode())) {
                    BaseConfigure baseConfigureFilter = new BaseConfigure();
                    baseConfigureFilter.setCode(filter.getCode());
                    baseConfigureFilter.setName(filter.getName());
                    baseConfigureFilter.setId(filter.getId());
                    baseConfigureFilter.setViewType(filter.getViewType());
                    dataSource.getDimConfMap().putIfAbsent(filter.getCode(), baseConfigureFilter);
                    filterCodeMap.get(filter.getCode()).getOperatorList().get(0).setTimeRange(TimeRange.NULL);
                    filterCodeMap.get(filter.getCode()).getOperatorList().get(0).getDataList().addAll(filter.getOperatorList().get(0).getDataList());
                    filterCodeMap.get(filter.getCode()).getOperatorList().get(0).getDataValueList().addAll(filter.getOperatorList().get(0).getDataValueList());
                } else {
                    filterCodeMap.put(filter.getCode(), filter);
                }

            }
        }


        dataSource.setFilterMap(filterCodeMap);

        Boolean isFeiYong = false;
        // 构建识别无权限的
        List<String> authMeasCodes = new ArrayList<>();
        UserContext userContext = userManager.getUserContext(itSpaceService.getAiSpaceById().getId(), UserThreadLocalUtil.getUserName());
        if (userContext == null || userContext.getAuthMeasures().isEmpty()) {
            dataSource.setDataRange(false);
            dataSource.setDataRange(false);
        } else {
            for (com.graphinsight.indicator.auto.entity.Measure measureItem : userContext.getAuthMeasures()) {
                if (null != measureItem && null != measureItem.getCode()) {
                    authMeasCodes.add(measureItem.getCode());
                }
            }
        }

        Iterator<Map.Entry<String, BaseConfigure>> iteratorMeas = dataSource.getMeasConfMap().entrySet().iterator();

        while (iteratorMeas.hasNext()) {
            Map.Entry<String, BaseConfigure> entry = iteratorMeas.next();
            // 计算表达式，权限通过
            if (entry.getValue().getCode().contains("MEAS_LDX_")) {
                configureCodeMap.put(entry.getValue().getCode(), entry.getValue());
                dataSource.getMeasConfList().add(entry.getValue());
                continue;
            }
            // 无权限，剔除
            if (!authMeasCodes.contains(entry.getValue().getCode())) {
                dataSource.getNoDataRangeList().add(entry.getKey());
                iteratorMeas.remove();
                continue;
            }

            if (removeMeas.contains(entry.getValue().getWordName())) {
                entry.getValue().setIsHide(true);
            }
            configureCodeMap.put(entry.getValue().getCode(), entry.getValue());
            if (!entry.getValue().getIsHide()) {
                dataSource.getMeasConfList().add(entry.getValue());
            }
            if (!entry.getValue().getIsHide() && entry.getValue().getCode().equals(feiYongCode)) {
                isFeiYong = true;
            }

        }

        dataSource.setMeasConfMap(configureCodeMap);


        if (dataSource.getMeasConfList().isEmpty()) {
            dataSource.setDataRange(false);
            dataSource.setDataRange(false);
        }
        dataSource.getDimConfList().addAll(dataSource.getDimConfMap().values());
        dataSource.getConfigureList().addAll(dataSource.getMeasConfList());
        dataSource.getConfigureList().addAll(dataSource.getDimConfList());
        dataSource.getFilterList().addAll(dataSource.getFilterMap().values());

        Boolean isAddFilter = true;
        Boolean isAddAccountFilter = true;

        List<String> defaulList = new ArrayList<>();
        defaulList.add(feiYongCategoryCode);
        defaulList.addAll(feiYongAccountCodeList);
        List<com.graphinsight.indicator.auto.entity.Dimension> dimensionDefaultList = dimensionMapper.selectList(Wrappers.<com.graphinsight.indicator.auto.entity.Dimension>lambdaQuery()
                .in(com.graphinsight.indicator.auto.entity.Dimension::getCode, defaulList));
        Map<String, com.graphinsight.indicator.auto.entity.Dimension> dimensionDefaultMap = dimensionDefaultList.stream().collect(Collectors.toMap(com.graphinsight.indicator.auto.entity.Dimension::getCode, item -> item, (ex, re) -> ex));


        if (isFeiYong) {
            for (Filter filter : dataSource.getFilterList()) {
                if (Objects.equals(filter.getCode(), feiYongCategoryCode)) {
                    isAddFilter = false;
                }

                if (feiYongAccountCodeList.contains(filter.getCode())) {
                    isAddAccountFilter = false;
                }
            }
        }


        if (isFeiYong && isAddFilter) {
            buildFYDefaultFilter(dataSource, dimensionDefaultMap, feiYongCategoryCode, feiYongCategoryValue, feiYongCategoryValueDesc);
        }

        if (isFeiYong && isAddAccountFilter) {
            buildFYDefaultFilter(dataSource, dimensionDefaultMap, feiYongAccountCode, feiYongAccountValue, feiYongAccountValueDesc);
        }


        // 构建不识别的
        for (WordSyntaxVo.WordItem wordItem : wordSyntaxVo.getWordItemList()) {
            if (Objects.equals(wordItem.getWordType(), "n")
                    || Objects.equals(wordItem.getWordType(), "kw")
                    || Objects.equals(wordItem.getSqlType(), "where")
                    || Objects.equals(wordItem.getSqlType(), "group")
            ) {
                if (wordItem.getBoolSet().isEmpty() && !wordItem.getBoolLikeSet().isEmpty()) {
                    dataSource.getUnKnowList().add(wordItem.getMatchText());
                }
            }
        }
        return dataSource;
    }

    DataSource buildFYDefaultFilter(DataSource dataSource, Map<String, com.graphinsight.indicator.auto.entity.Dimension> dimensionDefaultMap, String defaultCode, String defaultCodeValue, String defaultCodeValueDesc) {
        if (null != dimensionDefaultMap.get(defaultCode)) {
            com.graphinsight.indicator.auto.entity.Dimension dimension = dimensionDefaultMap.get(defaultCode);
            Filter filter = new Filter();
            filter.setId(Long.valueOf(dimension.getId()));
            filter.setCode(defaultCode);
            filter.setName(dimension.getCnName());
            Operator operator = new Operator();
            operator.setSqlOprType(SqlOprType.IN);
            operator.getDataList().add(defaultCodeValue);
            operator.getDataValueList().add(defaultCodeValueDesc);
            filter.getOperatorList().add(operator);
            dataSource.getFilterList().add(filter);
        }

        return dataSource;
    }

    // 构建表达式
    DataSource buildCalculate(DataSource dataSource, WordSyntaxVo wordSyntaxVo) {

        if (wordSyntaxVo.getNumerator().getUniqueKey() == null || wordSyntaxVo.getDenominator().getUniqueKey() == null) {
            return dataSource;
        }


        Map<String, WordSyntaxVo.WordItem> wordItemMap = wordSyntaxVo.getWordItemList().stream().collect(Collectors.toMap(WordSyntaxVo.WordItem::getMatchText, item -> item, (ex, re) -> ex));

        String calculateStr = "";
        String code = "";
        String name = "";
        Set<String> codeSet = new HashSet<>();
        Set<String> codeDimSet = new HashSet<>();
        List<String> measSub = null;
        if (wordSyntaxVo.getNumerator().getUniqueKey() != null) {
            measSub = Arrays.asList(wordSyntaxVo.getNumerator().getUniqueKey().split("~"));
            AiCalculateVo aiCalculateVo = calculateStr(dataSource.getMeasConfMap(), dataSource.getFilterMap(), measSub, wordItemMap, wordSyntaxVo.getNumerator().getSonTextList());
            calculateStr += aiCalculateVo.getFilterInfo();
            codeDimSet.addAll(aiCalculateVo.getFilterInfoSet());
            code += dataSource.getMeasConfMap().get(measSub.get(1)).getCode();
            codeSet.add(dataSource.getMeasConfMap().get(measSub.get(1)).getCode());
            name += String.join(",", wordSyntaxVo.getNumerator().getSonTextList()) + dataSource.getMeasConfMap().get(measSub.get(1)).getName();
        }

        // 如果分母为null，直接取指标
        if (wordSyntaxVo.getDenominator().getUniqueKey() != null) {
            measSub = Arrays.asList(wordSyntaxVo.getDenominator().getUniqueKey().split("~"));
            AiCalculateVo aiCalculateVo = calculateStr(dataSource.getMeasConfMap(), dataSource.getFilterMap(), measSub, wordItemMap, wordSyntaxVo.getDenominator().getSonTextList());
            calculateStr += "/" + aiCalculateVo.getFilterInfo();
            codeDimSet.addAll(aiCalculateVo.getFilterInfoSet());
            code += dataSource.getMeasConfMap().get(measSub.get(1)).getCode();
            codeSet.add(dataSource.getMeasConfMap().get(measSub.get(1)).getCode());
            name += "/" + String.join(",", wordSyntaxVo.getDenominator().getSonTextList()) + dataSource.getMeasConfMap().get(measSub.get(1)).getName();
        } else {
            measSub = Arrays.asList(wordSyntaxVo.getNumerator().getUniqueKey().split("~"));
            AiCalculateVo aiCalculateVo = calculateStr(dataSource.getMeasConfMap(), dataSource.getFilterMap(), measSub, wordItemMap, new ArrayList<>());
            calculateStr += "/" + aiCalculateVo.getFilterInfo();
            codeDimSet.addAll(aiCalculateVo.getFilterInfoSet());
            code += dataSource.getMeasConfMap().get(measSub.get(1)).getCode();
            codeSet.add(dataSource.getMeasConfMap().get(measSub.get(1)).getCode());
            name += "/" + String.join(",", wordSyntaxVo.getNumerator().getSonTextList()) + dataSource.getMeasConfMap().get(measSub.get(1)).getName();
        }

        String newCode = StringUtil.generateUUIDFromString(code).toString().replace("-", "");
        newCode = "MEAS_LDX_" + newCode;

        if (Objects.equals(wordSyntaxVo.getFormatType(), "ratio")) {
            calculateStr = "Concatenate(Format(" + calculateStr + "*100, '%,.2f'), '%')";
        } else {
            calculateStr = "Format(" + calculateStr + ", '%,.4f')";
        }
        BaseConfigure baseConfigure = new BaseConfigure();
        baseConfigure.setExpression(calculateStr);
        baseConfigure.setCode(newCode);
        baseConfigure.setCodeAlias(String.join(",", codeSet));
        baseConfigure.setDimCodeAlias(String.join(",", codeDimSet));
        baseConfigure.setName(name);

        dataSource.getMeasConfMap().put(name, baseConfigure);
        return dataSource;
    }

    @Autowired
    ITSpaceService itSpaceService;

    public AiCalculateVo calculateStr(Map<String, BaseConfigure> configureMap, Map<String, Filter> filterMap, List<String> measSub, Map<String, WordSyntaxVo.WordItem> wordItemMap, List<String> subList) {

        Boolean isFeiYong = false;
        AiCalculateVo aiCalculateVo = new AiCalculateVo();
        Set<String> codeDimSet = new HashSet<>();
        String measStr = "[";
        if (null != configureMap.get(measSub.get(1))) {
            if (Objects.equals(configureMap.get(measSub.get(1)).getCode(), feiYongCode)) {
                isFeiYong = true;
            }
            measStr += configureMap.get(measSub.get(1)).getCode() + "]";
        }
        String filters = "filters:(";
        if (subList.isEmpty()) {
            aiCalculateVo.setFilterInfo("Calculate(" + measStr + ")");
            if (Objects.equals(configureMap.get(measSub.get(1)).getCode(), feiYongCode)) {
                filters += "[" + feiYongCategoryCode + "] in '" + feiYongCategoryValue + "',";
                filters += "[" + feiYongAccountCode + "] in '" + feiYongAccountValue + "',";
                filters = filters.substring(0, filters.length() - 1) + ")";
                String info = "Calculate(" + measStr + "," + filters + ")";
                aiCalculateVo.setFilterInfo(info);
            }
            return aiCalculateVo;
        }


        Map<String, List<String>> codeMap = new HashMap<>();
        for (String sonText : subList) {
            if (null != filterMap.get(sonText) && null != wordItemMap.get(sonText)) {
                Filter filter = filterMap.get(sonText);
                WordSyntaxVo.WordItem wordItem = wordItemMap.get(sonText);
                if (codeMap.get(filter.getCode()) != null) {
                    codeMap.get(filter.getCode()).addAll(wordItem.getValueList());
                } else {
                    List<String> filterList = new ArrayList<>(wordItem.getValueList());
                    codeMap.put(filter.getCode(), filterList);
                }
            }
        }

        Boolean isAddDefault = true;
        Boolean isAddAccountDefault = true;
        for (String key : codeMap.keySet()) {
            log.info("Key: Value {} ,{}", key, codeMap.get(key));
            if (Objects.equals(key, feiYongCategoryCode)) {
                isAddDefault = false;
            }
            if (feiYongAccountCodeList.contains(key)) {
                isAddAccountDefault = false;
            }
            codeDimSet.add(key);
            filters += "[" + key + "] in '" + String.join(",", codeMap.get(key)) + "',";
        }
        if (isFeiYong && isAddDefault) {
            filters += "[" + feiYongCategoryCode + "] in '" + feiYongCategoryValue + "',";
        }
        if (isFeiYong && isAddAccountDefault) {
            filters += "[" + feiYongAccountCode + "] in '" + feiYongAccountValue + "',";
        }
        filters = filters.substring(0, filters.length() - 1) + ")";

        String info = "Calculate(" + measStr + "," + filters + ")";


        aiCalculateVo.getFilterInfoSet().addAll(codeDimSet);
        aiCalculateVo.setFilterInfo(info);
        return aiCalculateVo;
    }

    public DataSource buildDefaultOrder(DataSource dataSource) {
        // todo 临时处理，如果有同环比，则不设置默认排序
//        if (dataSource.isShowRatio()) {
//            return dataSource;
//        }
        if (dataSource.getExitOrder()) {
            return dataSource;
        }
        Boolean dimDefault = false;
        for (BaseConfigure baseConfigure : dataSource.getConfigureList()) {
            Order order = new Order();
            if (!Objects.equals(dataSource.getOrderType(), "")) {
                if (baseConfigure.getCode().contains("MEAS")) {
                    if (dataSource.getOrderType().equals("asc")) {
                        order.setSortType(SortType.ASC);
                    } else {
                        order.setSortType(SortType.DESC);
                    }
                    baseConfigure.setOrder(order);
                }
            } else {
                if (!dimDefault) {
                    if (dataSource.getOrderType().equals("asc")) {
                        order.setSortType(SortType.ASC);
                    } else {
                        order.setSortType(SortType.DESC);
                    }
                    baseConfigure.setOrder(order);
                    dimDefault = true;
                }
            }
        }

        return dataSource;
    }

    // 默认时间范围限制
    public DataSource buildDefaultFilter(DataSource dataSource) {
        // 如果没有指标，不设置默认条件
        if (dataSource.getMeasConfList().isEmpty()) {
            return dataSource;
        }
        // 如果没有过滤条件限制，设置默认日期
        if (!dataSource.getFilterList().isEmpty()) {
            return dataSource;
        }
        // 根据当前的指标血缘，找到时间维度；优先使用自然日期，取最近一年的数据
        // 如果维度是月，取自然日期月，如果维度是日 取自然日期日


        // 如果有时间维度
        DimensionCache dimensionCache = null;
        if (dataSource.getIsDimDate()) {
            BaseConfigure dataDateConfig = dataSource.getConfigureList().stream().filter(baseConfigure -> null != baseConfigure.getViewType() && ViewType.isDate(baseConfigure.getViewType().getValue())).findFirst().orElse(null);
            if (dataDateConfig != null) {
                dimensionCache = cacheManager.getDimensionCache(dataDateConfig.getId().intValue());
            }
        } else {
            // 获取月的
            dimensionCache = getDateDimInfo("month", dataSource.getBoolValidSet());
        }

        if (dimensionCache == null) {
            dimensionCache = getDateDimInfo("month", dataSource.getBoolSet());
        }
        if (dimensionCache == null) {
            return dataSource;
        }
        Filter filter = new Filter();

        filter.setCode(dimensionCache.getCode());
        filter.setName(dimensionCache.getCnName());
        filter.setViewType(ViewType.findByInt(dimensionCache.getDimension().getViewType()).orElse(null));
        filter.setId(Long.valueOf(dimensionCache.getId()));
        Operator operator = new Operator();

        operator.setSqlOprType(SqlOprType.BETEEN);
        operator.setTimeRange(TimeRange.DATE);
        // 获取最近一年的数据

        List<String> defaultNlpTimeList = ViewType.getDefaultNlpTime(dimensionCache.getDimension().getViewType());
        operator.getDataList().addAll(defaultNlpTimeList);
        filter.getOperatorList().add(operator);
        dataSource.getFilterList().add(filter);
        dataSource.setIsDefaultFilter(true);
        return dataSource;
    }

    // 设置同环比 单个指标 同时必须有日期维度 才可以设置
    public DataSource buildRatio(DataSource dataSource, WordSyntaxVo wordSyntaxVo) {
        Boolean useFilterFlag = false;
        BaseConfigure baseFilterConfigure = null;

        for (Filter filter : dataSource.getFilterList()) {
            // 增加过滤条件中的时间维度
            if (ViewType.isDate(filter.getViewType().getValue())
                    && !filter.getOperatorList().isEmpty()
                    && !filter.getOperatorList().get(0).getDataList().isEmpty())
                if (dataSource.isShowRatio()) {
                    useFilterFlag = true;
                    baseFilterConfigure = new BaseConfigure();
                    baseFilterConfigure.setViewType(filter.getViewType());
                    baseFilterConfigure.setCode(filter.getCode());
                    baseFilterConfigure.setName(filter.getName());
                    baseFilterConfigure.setId(filter.getId());
//                    if (!dataSource.getIsDimDate()) {
//                        filter.getOperatorList().get(0).getDataList().set(0, filter.getOperatorList().get(0).getDataList().get(1));
//                    }
                    break;
                } else {
                    if (!dataSource.getIsDimDate() && Objects.equals(filter.getOperatorList().get(0).getDataList().get(0), filter.getOperatorList().get(0).getDataList().get(1))) {
                        useFilterFlag = true;
                        baseFilterConfigure = new BaseConfigure();
                        baseFilterConfigure.setViewType(filter.getViewType());
                        baseFilterConfigure.setCode(filter.getCode());
                        baseFilterConfigure.setName(filter.getName());
                        baseFilterConfigure.setId(filter.getId());
                        break;
                    }
                }

        }

        // 如果是当个指标，必须显示同环比
        if (dataSource.isSingle()) {
            // 只有一个指标，并且没有维度
            if (dataSource.getConfigureList().size() == 1 && useFilterFlag) {
                dataSource.setShowRatio(true);
            }
        }
        if (dataSource.isShowRatio()) {
            if (!dataSource.getIsDimDate()) {
                dataSource.getRatioConfigList().add(baseFilterConfigure);
                dataSource.getConfigureList().add(baseFilterConfigure);
            }

            for (BaseConfigure baseConfigure : dataSource.getConfigureList()) {
                if (baseConfigure.getCode().contains("MEAS")) {
                    List<Ratio> ratioList = new ArrayList<>();
                    Map<String, Integer> mapSettings = new HashMap<>();
                    mapSettings.put("type", 5);
                    String typeString = com.alibaba.fastjson.JSON.toJSONString(mapSettings);

                    Ratio ratio = new Ratio();
                    if (wordSyntaxVo.getNatureRatioFlag() && wordSyntaxVo.getCompareRatio().getSubDate() != null) {
                        baseConfigure.setRatioColumnType(RatioColumnType.IN);
                        ratio.setRatioType(RatioType.FIEXED);
                        ratio.setRatioExpType(RatioExpType.DIFFPERCENTAGE);
                        ratio.setSettings(typeString);
                        ratio.setRatioValue(wordSyntaxVo.getCompareRatio().getSubDate());
                        ratioList.add(ratio);
                    } else {
                        ratio.setRatioType(RatioType.MONTHONMONTH);
                        ratio.setRatioExpType(RatioExpType.DIFFPERCENTAGE);
                        ratio.setSettings(typeString);
                        ratioList.add(ratio);

                        Ratio ratioYear = new Ratio();
                        ratioYear.setRatioType(RatioType.YEARYEMOM);
                        ratioYear.setRatioExpType(RatioExpType.DIFFPERCENTAGE);
                        ratioYear.setSettings(typeString);
                        ratioList.add(ratioYear);

                    }
                    baseConfigure.setRatioList(ratioList);
                }
            }

            Map<String, Object> stringObjectMap = getRatioRangeTimeConfig(dataSource);
            dataSource.setRatioRangeTime(stringObjectMap);
            dataSource.setSingle(true);
            return dataSource;
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
            for (Filter filter : filterInfoList) {
                if (ViewType.isDate(filter.getViewType().getValue())) {
                    List<Operator> operatorListItem = filter.getOperatorList();
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
                            case 5:
                                String lastDate = endDate;
                                endDate = endDate.substring(0, lastDate.length() - 1);
                                lastDate = lastDate.substring(lastDate.length() - 1);
                                inputFormatter = DateTimeFormatter.ofPattern("yyyy");
                                Year yearQ = Year.parse(endDate, inputFormatter);
                                Year beforeYearQ = yearQ.minusYears(1);
                                outputFormatter = DateTimeFormatter.ofPattern("yyyy");
                                endStr = yearQ.format(outputFormatter) + lastDate;
                                startStr = beforeYearQ.format(outputFormatter) + lastDate;
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
                    break;
                }

            }
        }
        return stringObjectMap;
    }


    private DimensionCache getDateDimInfo(String wordType, Set<Integer> boolSet) {
        MetadataCache metadataCache = cacheManager.getMetadataCache();

        Map<String, String> dateDimMap = new HashMap<>();
        dateDimMap.put("day", "D");
        dateDimMap.put("month", "M");
        dateDimMap.put("year", "Y");
        dateDimMap.put("quarter", "Q");


        Map<String, String> dateWordDimMap = new HashMap<>();
        dateWordDimMap.put("day", "日");
        dateWordDimMap.put("month", "月");
        dateWordDimMap.put("year", "年");
        dateWordDimMap.put("quarter", "季");

        com.graphinsight.indicator.auto.entity.Dimension dimensionCommonInfo = null;
        com.graphinsight.indicator.auto.entity.Dimension dimensionNatureInfo = null;
        com.graphinsight.indicator.auto.entity.Dimension dimensionLikeInfo = null;
        for (com.graphinsight.indicator.auto.entity.Dimension dimension : metadataCache.getAllDimensionMap().values()) {
            if (!boolSet.contains(dimension.getId())) {
                continue;
            }
            if (!ViewType.isDate(dimension.getViewType())) {
                continue;
            }
            dimensionCommonInfo = dimension;

            if (null != dateDimMap.get(wordType)) {
                String cnName = "自然日期_" + dateDimMap.get(wordType);
                if (Objects.equals(dimension.getCnName(), cnName)) {
                    dimensionNatureInfo = dimension;
                    break;
                }
            }
            if (null != dateWordDimMap.get(wordType)) {
                if (dimension.getCnName().contains(dateWordDimMap.get(wordType))) {
                    dimensionLikeInfo = dimension;
                }
            }

        }

        DimensionCache dimensionCache = new DimensionCache();
        if (null != dimensionNatureInfo) {
            BeanUtils.copyProperties(dimensionNatureInfo, dimensionCache);
            dimensionCache.setDimension(dimensionNatureInfo);
            return dimensionCache;
        }

        if (null != dimensionLikeInfo) {
            BeanUtils.copyProperties(dimensionLikeInfo, dimensionCache);
            dimensionCache.setDimension(dimensionLikeInfo);
            return dimensionCache;
        }

        if (null != dimensionCommonInfo) {
            BeanUtils.copyProperties(dimensionCommonInfo, dimensionCache);
            dimensionCache.setDimension(dimensionCommonInfo);
            return dimensionCache;
        }
        return null;
    }


}

