package com.graphinsight.indicator.service.wordNlp.chain;


import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.graphinsight.indicator.auto.entity.DimAllValuesInfo;
import com.graphinsight.indicator.auto.mapper.AiBoardInfoMapper;
import com.graphinsight.indicator.auto.mapper.DimAllValuesMapper;
import com.graphinsight.indicator.auto.mapper.DimensionMapper;
import com.graphinsight.indicator.auto.entity.AiBoardInfo;
import com.graphinsight.indicator.manager.CacheManager;
import com.graphinsight.indicator.model.Measure;
import com.graphinsight.indicator.model.vo.WordSyntaxVo;
import com.graphinsight.indicator.service.IndicatorService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import com.graphinsight.indicator.auto.entity.Dimension;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 *
 */
@Slf4j
@Component
public class WordDiscernChain extends AbstractWordChain {

    @Autowired
    private IndicatorService indicatorService;

    @Autowired
    CacheManager cacheManager;

    @Autowired
    DimensionMapper dimensionMapper;

    @Autowired
    private DimAllValuesMapper dimAllValuesMapper;

    @Autowired
    AiBoardInfoMapper aiBoardInfoMapper;


    @Override
    protected WordSyntaxVo execProcess(WordSyntaxVo wordSyntaxVo) {
        wordSyntaxVo = boardMark(wordSyntaxVo);
        if (wordSyntaxVo.getIsBoard()) {
            return wordSyntaxVo;
        }
        wordSyntaxVo = measMark(wordSyntaxVo);

        wordSyntaxVo = wordMark(wordSyntaxVo);
        wordSyntaxVo = wordLikeMark(wordSyntaxVo);
        return wordSyntaxVo;
    }

    public WordSyntaxVo boardMark(WordSyntaxVo wordSyntaxVo) {
        List<String> queryList = new ArrayList<>();
        for (WordSyntaxVo.WordItem wordItem : wordSyntaxVo.getWordItemList()) {
            if (Objects.equals(wordItem.getWordType(), "uj")
                    || wordItem.getStandText() != null
                    || Objects.equals(wordItem.getMatchType(), "measure")
                    || wordItem.getMatchText().length() <= 1) {
                continue;
            }
            queryList.add(wordItem.getMatchText());
        }

        List<AiBoardInfo> aiBoardInfoList = aiBoardInfoMapper.selectListByName(queryList);


        Map<String, AiBoardInfo> boardInfoMap = aiBoardInfoList.stream().collect(Collectors.toMap(measure -> measure.getBoardName().toLowerCase(), Function.identity(), (re, ex) -> re));

        Boolean isBoard = false;
        for (WordSyntaxVo.WordItem wordItem : wordSyntaxVo.getWordItemList()) {
            if (wordItem.getWordType() == null || Objects.equals(wordItem.getWordType(), "uj")) {
                continue;
            }
            String matchText = wordItem.getMatchText().toLowerCase();
            if (null != boardInfoMap.get(matchText)) {
                wordItem.setMatchType("board");
                wordItem.setMatchMethod("exact");
                wordItem.getValueList().add(boardInfoMap.get(matchText).getBoardName() + "~" + boardInfoMap.get(matchText).getBoardUrl());
                wordSyntaxVo.setIsBoard(true);
                break;
            }

//            if (isBoard) {
//                break;
//            }
//            String matchText = wordItem.getMatchText().toLowerCase();
//            for (AiBoardInfo boardInfo : aiBoardInfoList) {
//                if (boardInfo.getBoardName().toLowerCase().contains(matchText)) {
//                    if(Objects.equals(boardInfo.getBoardUrl(), "")){
//                        continue;
//                    }
//                    wordItem.setMatchType("board");
//                    wordItem.setMatchMethod("exact");
//                    wordItem.getValueList().add(boardInfo.getBoardName() + "~" + boardInfo.getBoardUrl());
//                    isBoard = true;
//                    wordSyntaxVo.setIsBoard(true);
//                    break;
//                }
//            }

        }

        return wordSyntaxVo;

    }

    public WordSyntaxVo measMark(WordSyntaxVo wordSyntaxVo) {
        List<Measure> measureList = this.indicatorService.listAllMeasure();
        Map<String, Measure> measureMap = measureList.stream().collect(Collectors.toMap(measure -> measure.getName().toLowerCase(), Function.identity(), (re, ex) -> re));
        Integer measNum = 0;
        for (WordSyntaxVo.WordItem wordItem : wordSyntaxVo.getWordItemList()) {
            if (wordItem.getWordType() == null || Objects.equals(wordItem.getWordType(), "uj")) {
                continue;
            }
            if (null != measureMap.get(wordItem.getMatchText().toLowerCase())) {
                Measure measure = measureMap.get(wordItem.getMatchText().toLowerCase());
                wordItem.setStandText(measure.getName());
                wordItem.setMatchType("measure");
                wordItem.setMatchMethod("exact");
                wordItem.getBoolSet().add(Math.toIntExact(measure.getId()));
                wordItem.getBoolLikeSet().add(Math.toIntExact(measure.getId()));
                wordItem.getBoolLikeSet().add(Math.toIntExact(measure.getId()));
                wordSyntaxVo.getMatchTextList().add(measure.getName());
                wordSyntaxVo.setMeasNum(++measNum);

                wordSyntaxVo.getRelatedSet().getMeasureSet().add(Math.toIntExact(measure.getId()));
                if (wordSyntaxVo.getRelatedSet().getDimensionSet().isEmpty()) {
                    wordSyntaxVo.getRelatedSet().getDimensionSet().addAll(cacheManager.getMeasureCache(Math.toIntExact(measure.getId())).getRelatedDimensionIds());
                } else {
                    wordSyntaxVo.getRelatedSet().getDimensionSet().retainAll(cacheManager.getMeasureCache(Math.toIntExact(measure.getId())).getRelatedDimensionIds());
                }
            }
        }
        return wordSyntaxVo;
    }


    // 指标 维度 维值
    // 获取分词后的结果
    public WordSyntaxVo wordMark(WordSyntaxVo wordSyntaxVo) {

        List<String> queryList = new ArrayList<>();
        for (WordSyntaxVo.WordItem wordItem : wordSyntaxVo.getWordItemList()) {
            if (Objects.equals(wordItem.getWordType(), "uj")
                    || wordItem.getStandText() != null
                    || Objects.equals(wordItem.getMatchType(), "measure")
                    || wordItem.getMatchText().length() <= 1) {
                continue;
            }
            queryList.add(wordItem.getMatchText());
        }

        //  维值查询
        List<DimAllValuesInfo> validDimList = dimAllValuesMapper.selectRealListByName(queryList, wordSyntaxVo.getRelatedSet().getDimensionSet());

        Map<String, List<DimAllValuesInfo>> dimAllValuesMap = validDimList.stream().collect(Collectors.groupingBy(DimAllValuesInfo::getValueFormatText));
        Map<String, Integer> dateViewMap = new HashMap<>();
        dateViewMap.put("day", 1);
        dateViewMap.put("month", 3);
        dateViewMap.put("year", 5);
        dateViewMap.put("月", 3);
        dateViewMap.put("quarter", 4);

        List<Dimension> dimensionDateList = dimensionMapper.selectList(Wrappers.<Dimension>lambdaQuery()
                .in(Dimension::getViewType, Arrays.asList(1, 3, 4, 5)).select(Dimension::getId, Dimension::getCnName, Dimension::getCode, Dimension::getViewType));

        Map<Integer, List<Dimension>> dimensionDateMap = dimensionDateList.stream().collect(Collectors.groupingBy(Dimension::getViewType, Collectors.toList()));

        for (WordSyntaxVo.WordItem wordItem : wordSyntaxVo.getWordItemList()) {
            if (wordItem.getWordType() == null
                    || Objects.equals(wordItem.getMatchType(), "measure")
                    || Objects.equals(wordItem.getWordType(), "uj")

            ) {
                continue;
            }
            // 日期类型的设置血缘
            if (null != dateViewMap.get(wordItem.getWordType())) {
                wordItem.getBoolSet().addAll(dimensionDateMap.get(dateViewMap.get(wordItem.getWordType())).stream().map(Dimension::getId).collect(Collectors.toSet()));
                wordItem.getBoolLikeSet().addAll(dimensionDateMap.get(dateViewMap.get(wordItem.getWordType())).stream().map(Dimension::getId).collect(Collectors.toSet()));
                continue;
            }
            // 维度判断

            //
            if (!Objects.equals(wordItem.getMatchType(), "date")) {
                if (null != dimAllValuesMap.get(wordItem.getMatchText().toLowerCase())) {
                    List<DimAllValuesInfo> dimAllValuesInfoList = dimAllValuesMap.get(wordItem.getMatchText().toLowerCase());
                    wordItem.setStandText(dimAllValuesInfoList.get(0).getValueText());
                    wordItem.setMatchType("dimValue");
                    wordItem.setMatchMethod("exact");
                    wordItem.setSqlType("where");
                    wordSyntaxVo.getMatchTextList().add(wordItem.getMatchText());
                    // 所有可能得维值
                    dimAllValuesInfoList.forEach(dimAllValues -> {
                        wordItem.getValueList().add(dimAllValues.getValueKey() + "~" + dimAllValues.getDimId() + "~" + dimAllValues.getValueText());
                        wordItem.getBoolSet().add(dimAllValues.getDimId());
                        wordItem.getBoolLikeSet().add(dimAllValues.getDimId());
                    });
                }
            }

        }


        return wordSyntaxVo;

    }

    // 指标 维度 维值
    // 北京的pv
    // 试驾部门的pv
    public WordSyntaxVo wordLikeMark(WordSyntaxVo wordSyntaxVo) {

        Set<String> singWord = new HashSet<>();
        singWord.add("省");
        singWord.add("市");
        singWord.add("县");
        singWord.add("省份");
        List<String> queryList = new ArrayList<>();
        for (WordSyntaxVo.WordItem wordItem : wordSyntaxVo.getWordItemList()) {
            if (Objects.equals(wordItem.getWordType(), "uj")
                    || Objects.equals(wordItem.getMatchText(), ",")
                    || wordItem.getStandText() != null
                    || Objects.equals(wordItem.getMatchType(), "measure")
                    || (wordItem.getMatchText().length() <= 1 && !singWord.contains(wordItem.getMatchText()))) {
                continue;
            }
            queryList.add(wordItem.getMatchText());
        }
        if (queryList.isEmpty()) {
            return wordSyntaxVo;
        }
        //  维度精确查询
        List<Dimension> dimensionExactList = dimensionMapper.selectRealInfoByName(queryList, wordSyntaxVo.getRelatedSet().getDimensionSet());
        if (!dimensionExactList.isEmpty()) {
            Map<String, Dimension> dimensionExactMap = dimensionExactList.stream().collect(Collectors.toMap(dim -> dim.getCnName().toLowerCase(), Function.identity(), (re, ex) -> re));
            for (WordSyntaxVo.WordItem wordItem : wordSyntaxVo.getWordItemList()) {
                if (Objects.equals(wordItem.getWordType(), "uj")
                        || Objects.equals(wordItem.getMatchText(), ",")
                        || Objects.equals(wordItem.getMatchType(), "measure")
                        || wordItem.getStandText() != null ||
                        (wordItem.getMatchText().length() <= 1 && !singWord.contains(wordItem.getMatchText()))) {
                    continue;
                }

                if (null != dimensionExactMap.get(wordItem.getMatchText().toLowerCase())) {
                    Dimension dimension = dimensionExactMap.get(wordItem.getMatchText().toLowerCase());
                    wordItem.setStandText(dimension.getCnName());
                    wordItem.setMatchType("dimension");
                    wordItem.setMatchMethod("exact");
                    wordItem.getBoolSet().add(dimension.getId());
                    wordItem.getBoolLikeSet().add(dimension.getId());
                    wordSyntaxVo.getMatchTextList().add(dimension.getCnName());

                }
            }
        }
        //  维度查询
        List<Dimension> dimensionList = dimensionMapper.selectRealInfoByLikeName(queryList, wordSyntaxVo.getRelatedSet().getDimensionSet());
        //  维值查询

        List<DimAllValuesInfo> validDimList = dimAllValuesMapper.selectRealByLikeName(queryList, wordSyntaxVo.getRelatedSet().getDimensionSet());


        for (WordSyntaxVo.WordItem wordItem : wordSyntaxVo.getWordItemList()) {
            if (Objects.equals(wordItem.getWordType(), "uj") || Objects.equals(wordItem.getMatchMethod(), "exact") ||
                    (wordItem.getMatchText().length() <= 1 && !singWord.contains(wordItem.getMatchText()))) {
                continue;
            }


            String matchText = wordItem.getMatchText().toLowerCase();
            // 维度判断
            for (Dimension dimItem : dimensionList) {
                if (dimItem.getCnName().toLowerCase().contains(matchText)) {
                    wordItem.setMatchType("dimension");
                    wordItem.setMatchMethod("like");
                    wordItem.setSqlType("group");
                    wordItem.getBoolSet().add(dimItem.getId());
                    wordItem.getBoolLikeSet().add(dimItem.getId());
                    break;
                }
            }
            // 如果有识别到维度，不在识别维值
            if ("like".equals(wordItem.getMatchMethod())) {
                continue;
            }

            if (!Objects.equals(wordItem.getMatchType(), "date")) {
                // 维值判断
                for (DimAllValuesInfo dimValueItem : validDimList) {
                    if (dimValueItem.getValueText().toLowerCase().contains(matchText)) {
                        wordItem.setMatchType("dimValue");
                        wordItem.setSqlType("where");
                        wordItem.setMatchMethod("like");
                        wordItem.getValueList().add(dimValueItem.getValueKey() + "~" + dimValueItem.getDimId() + "~" + dimValueItem.getValueText());
                        wordItem.getBoolSet().add(dimValueItem.getDimId());
                        wordItem.getBoolLikeSet().add(dimValueItem.getDimId());
                    }
                }
            }

        }


        return wordSyntaxVo;

    }


}

