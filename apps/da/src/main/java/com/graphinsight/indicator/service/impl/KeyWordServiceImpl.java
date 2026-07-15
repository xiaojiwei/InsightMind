package com.graphinsight.indicator.service.impl;

import cn.hutool.core.io.resource.ClassPathResource;
import cn.hutool.core.io.resource.Resource;
import com.baomidou.dynamic.datasource.annotation.DS;
import com.graphinsight.indicator.controller.SmartAgentController;
import com.graphinsight.indicator.dao.DimAllValuesDao;
import com.graphinsight.indicator.enums.*;
import com.graphinsight.indicator.model.*;
import com.graphinsight.indicator.model.dto.BaseInfoDTO;
import com.graphinsight.indicator.service.ChartQueryService;
import com.graphinsight.indicator.service.IndicatorService;
import com.graphinsight.indicator.service.KeyWordService;
import com.graphinsight.indicator.util.StringUtil;
import com.graphinsight.indicator.util.UserThreadLocalUtil;
import io.lettuce.core.GeoArgs;
import lombok.extern.slf4j.Slf4j;
import org.ansj.domain.Result;
import org.ansj.domain.Term;
import org.ansj.splitWord.analysis.DicAnalysis;
import org.nlpcn.commons.lang.tire.domain.Forest;
import org.nlpcn.commons.lang.tire.domain.Value;
import org.nlpcn.commons.lang.tire.library.Library;
import org.owasp.esapi.ESAPI;
import org.owasp.esapi.codecs.MySQLCodec;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;

import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;
import javax.persistence.Query;
import java.io.InputStream;
import java.time.LocalDate;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Service
@DS("mysql")
@Slf4j
public class KeyWordServiceImpl implements KeyWordService {

    @Autowired
    private IndicatorService indicatorService;

    @PersistenceContext
    private EntityManager entityManager;

    @Autowired
    private ChartQueryService chartQueryService;


    @Override
    public PageData doAction2(String word) {

        // 先找指标
        Set<WordKey> wordKeySet = split(word);



        //获取维度code
        Set<DimAllValues> dimAllValuesSet = this.getDimCode(wordKeySet);
        Set<String> measCodeSet = this.getMeasCode(wordKeySet);

        Set<DimAllValues> ableDimCodeSet = new HashSet<>();
        String useMeasCode = null;
        DimAllValues useDimAllValues = null;

        // 构建dataSource
        DataSource dataSource = new DataSource();
        dataSource.setCacheStrategy(CacheStrategy.OVERWRITE);
        dataSource.setSpaceId(null);
        dataSource.setChartType(ChartType.LINE);
        dataSource.setUsername(UserThreadLocalUtil.getUserName());

        PageData pageData = chartQueryService.execQuery(dataSource);
        pageData.setDataSource(dataSource);

        return pageData;
    }

    @Override
    public PageData doAction(String word) {

        Set<WordKey> wordKeySet = split(word);
        //获取维度code
        Set<DimAllValues> dimAllValuesSet = this.getDimCode(wordKeySet);
        Set<String> measCodeSet = this.getMeasCode(wordKeySet);

        Set<DimAllValues> ableDimCodeSet = new HashSet<>();
        String useMeasCode = null;
        DimAllValues useDimAllValues = null;

        for (String measCode : measCodeSet) {

            for (DimAllValues dimAllValues : dimAllValuesSet) {

                String dimCode = dimAllValues.getDimCode();

                Set<String> paramDimSet = new HashSet<String>();
                paramDimSet.add(dimCode);
                Set<String> paramMeasSet = new HashSet<String>();
                paramMeasSet.add(measCode);

                if (Objects.equals(dimCode, "DIM_94b49e670fe84b7986c2bc09bca5b0f7")) {
                    log.info("xxxx is {}", dimCode);
                }

                boolean hasRelation = this.indicatorService.hasRelation(paramDimSet, paramMeasSet);
                if (hasRelation &&
                        (useDimAllValues == null ||
                                (this.getDimLevel(dimAllValues.getDimName()) < this.getDimLevel(useDimAllValues.getDimName()))
                        )
                ) {
                    useMeasCode = measCode;
                    useDimAllValues = dimAllValues;
                    ableDimCodeSet.add(dimAllValues);
                }
            }
        }


        //Set<String> dimCodeSet = ableDimCodeSet.stream().collect(Collectors.mapping(dimAllValues-> dimAllValues.getDimCode(), Collectors.toSet()));
        Set<String> dimCodeSet = new HashSet<>();
        dimCodeSet.add(useDimAllValues.getDimCode());
        DimAllValues dimDate = this.getDateDim(wordKeySet, dimCodeSet, useMeasCode);

        DataSource dataSource = new DataSource();
        dataSource.setCacheStrategy(CacheStrategy.OVERWRITE);
        dataSource.setSpaceId(null);
        dataSource.setChartType(ChartType.LINE);
        dataSource.setUsername(UserThreadLocalUtil.getUserName());

        List<BaseConfigure> configureList = new LinkedList<>();
        BaseConfigure measureConfigure = new BaseConfigure();
        measureConfigure.setCode(useMeasCode);

        Order order = this.getOrder(wordKeySet);
        if (null != order) {
            measureConfigure.setOrder(order);
        }
        configureList.add(measureConfigure);

        String dimCode = useDimAllValues.getDimCode();

        //维度
        BaseConfigure dimensionConfigure = new BaseConfigure();
        dimensionConfigure.setCode(dimCode);
        configureList.add(dimensionConfigure);

        dataSource.setPageable(false);
        dataSource.setConfigureList(configureList);
        log.info("开始查询");


        if (useDimAllValues.isFromValue()) {

            //维度筛选条件
            Filter filter = new Filter();
            filter.setCode(dimCode);

            Operator operator = new Operator();
            operator.setSqlOprType(SqlOprType.IN);
            operator.getDataList().add(useDimAllValues.getValueKey());

            filter.getOperatorList().add(operator);

            dataSource.getFilterList().add(filter);

        }

        if (null != dimDate) {

            Filter dateFilter = new Filter();
            dateFilter.setCode(dimDate.getDimCode());

            //筛选条件 //日期筛选维度
            Integer num = getNumber(wordKeySet);
            //是否含有谓词
            Boolean has = this.hasPredicate(wordKeySet);
            Operator operator1 = new Operator();
            if (has) {
                operator1.setSqlOprType(SqlOprType.GREATER_THAN_OR_EQUAL);
                operator1.getDataList().add("T-M-" + num);
                operator1.setTimeRange(TimeRange.DATE);
            } else {

                operator1.setSqlOprType(SqlOprType.IN);
                String data = this.getDataList(dimDate, num, SqlOprType.IN);
                operator1.getDataList().add(data);
                operator1.setTimeRange(TimeRange.DATE);

            }

            dateFilter.getOperatorList().add(operator1);
            dataSource.getFilterList().add(dateFilter);

        }

        Integer topN = this.getTopN(wordKeySet);
        if (null != topN) {
            dataSource.setPageSize(topN);
            dataSource.setChartType(ChartType.TABLE);
        }

        PageData pageData = chartQueryService.execQuery(dataSource);
        pageData.setDataSource(dataSource);

        return pageData;

    }

    private Integer getTopN(Set<WordKey> wordKeySet) {

        Integer topN = null;
        boolean hasTop = false;
        for (WordKey wordKey : wordKeySet) {
            if ("f".equalsIgnoreCase(wordKey.getPart()) && "前".equalsIgnoreCase(wordKey.getKey())) {
                hasTop = true;
            }

            if ("m".equalsIgnoreCase(wordKey.getPart())) {
                try {
                    topN = Integer.valueOf(wordKey.getKey());
                } catch (Exception ex) {
//                    ex.printStackTrace();
                }
            }
        }

        if (!hasTop) {
            topN = null;
        }

        return topN;

    }

    private Order getOrder(Set<WordKey> wordKeySet) {

        Order order = null;
        boolean hasOrder = false;
        SortType sortType = SortType.DESC;
        for (WordKey wordKey : wordKeySet) {
            if ("v".equalsIgnoreCase(wordKey.getPart()) || "f".equalsIgnoreCase(wordKey.getPart())) {
                hasOrder = true;
            }
            if ("f".equalsIgnoreCase(wordKey.getPart())) {
                if (wordKey.getKey().indexOf("后") >= 0) {
                    sortType = SortType.ASC;
                }
            }
        }

        if (hasOrder) {
            order = new Order();
            order.setSortType(sortType);
        }

        return order;

    }

    private String getDataList(DimAllValues dimDate, Integer num, SqlOprType sqlOprType) {

        String data = "";
        ViewType viewType = dimDate.getViewType();
        if (SqlOprType.IN.equals(sqlOprType)) {

            if (ViewType.MONTH.equals(viewType)) {
                LocalDate localDate = LocalDate.now();
                Integer year = localDate.getYear();
                String month = num < 10 ? String.valueOf(num) : "0" + String.valueOf(num);
                data = year + month;
            }

        }

        return data;

    }

    private Boolean hasPredicate(Set<WordKey> wordKeySet) {

        Boolean has = false;
        Object[] wordKeys = wordKeySet.toArray();
        for (int i = 0; i < wordKeys.length; i++) {

            WordKey wordKey = (WordKey) wordKeys[i];
            String nature = wordKey.getPart();
            if (("m".equalsIgnoreCase(nature) || "t".equalsIgnoreCase(nature)) && i > 0) {
                WordKey predicateWordKey = (WordKey) wordKeys[i - 1];
                if ("a".equalsIgnoreCase(predicateWordKey.getPart())) {
                    has = true;
                    break;
                }
            }

        }

        return has;

    }

    private Integer getNumber(Set<WordKey> wordKeySet) {

        Integer num = 0;
        String regEx = "[^0-9]";
        Pattern p = Pattern.compile(regEx);
        for (WordKey wordKey : wordKeySet) {

            String nature = wordKey.getPart();
            if ("m".equalsIgnoreCase(nature) || "t".equalsIgnoreCase(nature)) {
                String value = wordKey.getKey();
                value = this.replaceWord(value);
                Matcher m = p.matcher(value);
                String number = m.replaceAll("").trim();
                num = Integer.valueOf(number);
                break;
            }

        }

        return num;

    }

    private Integer getDimLevel(String dimName) {
        Result result = DicAnalysis.parse(dimName);
        List<Term> termList = result.getTerms();
        if (termList.size() > 2) {
            return 100;
        } else {
            Map<String, Integer> natureCountMap = new HashMap<>();
            for (Term term : termList) {
                String nature = term.getNatureStr();

                Integer count = natureCountMap.get(nature);
                if (null == count) {
                    count = 0;
                }
                natureCountMap.put(nature, ++count);

            }

            for (Map.Entry<String, Integer> natureEntry : natureCountMap.entrySet()) {

                String nature = natureEntry.getKey();
                Integer count = natureEntry.getValue();

                if (nature.indexOf("n") >= 0) {
                    return 10 - count;
                }

            }

        }

        return 100;

    }

    private Set<String> getMeasCode(Set<WordKey> wordKeySet) {

        Set<String> measCodeSet = new HashSet<>();
        Measure resultMeasure = null;

        for (WordKey wordKey : wordKeySet) {

            if ("uj".equalsIgnoreCase(wordKey.getPart())) {
                continue;
            }

            String measName = wordKey.getKey();
            measName = SmartAgentController.replaceKey(measName);
            List<Measure> measureList = this.indicatorService.listMeasureByName(measName);

            if (null != measureList && measureList.size() > 0) {

                for (Measure measure : measureList) {
                    if (resultMeasure == null) {
                        resultMeasure = measure;
                    }
                    if (measName.equalsIgnoreCase(measure.getName())) {
                        resultMeasure = measure;
                    }
                }

            }

        }

        if (null != resultMeasure) {
            measCodeSet.add(resultMeasure.getCode());
        }

        return measCodeSet;

    }

    private DimAllValues getDateDim(Set<WordKey> wordKeySet, Set<String> dimCodeSet, String measCode) {

        DimAllValues useDimAllValues = null;

        Set<String> measCodeSet = new HashSet<>();
        measCodeSet.add(measCode);
        List<BaseInfoDTO> dimBaseInfoList = this.indicatorService.listDateDimension(dimCodeSet, measCodeSet);

        if (!CollectionUtils.isEmpty(dimBaseInfoList)) {
            for (BaseInfoDTO baseInfoDTO : dimBaseInfoList) {

                String column = baseInfoDTO.getEnName();
                Integer viewType = baseInfoDTO.getViewType();

                if (this.isEquals(wordKeySet, viewType) && (null == useDimAllValues || column.indexOf("indicator_natural") >= 0)) {

                    useDimAllValues = new DimAllValues();
                    useDimAllValues.setDimCode(baseInfoDTO.getCode());
                    useDimAllValues.setViewType(ViewType.findByInt(viewType).get());

                }
            }
        }

        return useDimAllValues;

    }

    private String replaceWord(String value) {

        value = value.replaceAll("天", "日");
        value = value.replaceAll("一", "1");
        value = value.replaceAll("二", "2");
        value = value.replaceAll("三", "3");
        value = value.replaceAll("四", "4");
        value = value.replaceAll("五", "5");
        value = value.replaceAll("六", "6");
        value = value.replaceAll("七", "7");
        value = value.replaceAll("八", "8");
        value = value.replaceAll("九", "9");

        value = value.replaceAll("两", "2");
        value = value.replaceAll("个", "");
        value = value.replaceAll("份", "");

        value = value.replaceAll("G9", "XP01");
        value = value.replaceAll("G6", "XP02");
        value = value.replaceAll("X9", "XP03");

        return value;

    }

    private boolean isEquals(Set<WordKey> wordKeySet, Integer viewType) {

        String dimKeyName = "";
        if (ViewType.DAY.getValue().equals(viewType)) {
            dimKeyName = "日";
        } else if (ViewType.WEEK.getValue().equals(viewType)) {
            dimKeyName = "周";
        } else if (ViewType.MONTH.getValue().equals(viewType)) {
            dimKeyName = "月";
        } else if (ViewType.SEASON.getValue().equals(viewType)) {
            dimKeyName = "季";
        } else if (ViewType.YEAR.getValue().equals(viewType)) {
            dimKeyName = "年";
        }

        boolean equals = false;
        for (WordKey wordKey : wordKeySet) {
            String nature = wordKey.getPart();
            String value = wordKey.getKey();
            if ("m".equalsIgnoreCase(nature) || "t".equalsIgnoreCase(nature)) {

                value = this.replaceWord(value);

                if (value.indexOf(dimKeyName) >= 0) {
                    equals = true;
                    break;
                }

            }
        }

        return equals;

    }

    private Set<DimAllValues> getDimCode(Set<WordKey> wordKeySet) {

        Set<DimAllValues> dimAllValuesSet = new HashSet<>();

        for (WordKey wordKey : wordKeySet) {

            if (wordKey.getPart().indexOf("n") < 0) {
                continue;
            }


            List<Dimension> dimensionList = this.indicatorService.listAllDimension();
            boolean isHasDim = false;
            for (Dimension dim : dimensionList) {

                if (Objects.equals(dim.getName(), wordKey.getKey())) {
                    DimAllValues dimAllValues = new DimAllValues();
                    dimAllValues.setFromValue(false);
                    dimAllValues.setDimCode(dim.getCode());
                    dimAllValues.setDimName(dim.getName());
                    dimAllValuesSet.add(dimAllValues);
                    isHasDim = true;
                    break;
                }

            }

            if (isHasDim) {
                continue;
            }

            String enters = ESAPI.encoder().encodeForSQL(new MySQLCodec(MySQLCodec.Mode.ANSI), wordKey.getKey());
            String hql = "select dav From DimAllValues as dav where dav.valueText like '%" + enters + "%'";
            Query query = this.entityManager.createQuery(hql);

            List list = query.getResultList();
            if (null != list && list.size() > 0) {

                for (Object dim : list) {

                    DimAllValues dimAllValues = (DimAllValues) dim;
                    dimAllValues.setFromValue(true);
                    dimAllValuesSet.add(dimAllValues);

                }

            }


        }

        return dimAllValuesSet;

    }

    private static List<Value> VALUSE_LIST = new ArrayList<Value>();

    private static Forest FOREST = null;

    private Set<WordKey> split(String word) {

        word = replaceWord(word);
        // VALUSE_LIST.add(new Value(word, "nl", "1000"));

        Set<WordKey> wordKeySet = new LinkedHashSet<>();

        if (null == FOREST) {
            /**
             * 自定义词库
             */
            List<Measure> measureList = this.indicatorService.listAllMeasure();
            for (Measure measure : measureList) {
                //自定义词、词性。此处指标、维度、维度值都定义成名词。
                Value v = new Value(measure.getName(), "n", "1000");
                VALUSE_LIST.add(v);
            }

            String hql = "select wv From WordValues as wv";
            Query query = this.entityManager.createQuery(hql);
            List<WordValues> wordValuesList = query.getResultList();

            if (!CollectionUtils.isEmpty(wordValuesList)) {
                for (WordValues wordValues : wordValuesList) {
                    Value v = new Value(wordValues.getValue(), "n", "1000");
                    VALUSE_LIST.add(v);
                }
            }

            Value v1 = new Value("最近", "a", "1000");
            VALUSE_LIST.add(v1);

            List<Dimension> dimensionList = this.indicatorService.listAllDimension();
            for (Dimension dim : dimensionList) {

                if (!ViewType.CHARACTER.equals(dim.getViewType())) {
                    continue;
                }

                Value dimV = new Value(dim.getName(), "n", "1000");
                VALUSE_LIST.add(dimV);
            }


            FOREST = Library.makeForest(VALUSE_LIST);

        }

        Result result = DicAnalysis.parse(word, FOREST);

        List<Term> termList = result.getTerms();
        for (Term term : termList) {

            String name = term.getName();
            WordKey wordKey = new WordKey();
            name = SmartAgentController.replaceKey(name);
            wordKey.setKey(name);

            String hql = "select wv From WordValues as wv where wv.value = '" + name + "'";
            Query query = this.entityManager.createQuery(hql);
            List<WordValues> wordValuesList = query.getResultList();
            if (!CollectionUtils.isEmpty(wordValuesList)) {
                for (WordValues wordValues : wordValuesList) {
                    wordKey.setKey(wordValues.getKey());
                }
            }

            wordKey.setPart(term.getNatureStr());

            wordKeySet.add(wordKey);

        }

        return wordKeySet;

    }

}
