package com.graphinsight.indicator.service.wordNlp.chain;

import com.graphinsight.indicator.model.vo.CompareVo;
import com.graphinsight.indicator.model.vo.TextNodeVo;
import com.graphinsight.indicator.model.vo.WordSyntaxVo;
import com.graphinsight.indicator.service.wordNlp.WordSyntax;
import io.swagger.models.auth.In;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;


@Component
@Slf4j
public class SubConV2Chain extends AbstractWordChain {


    @Value("${nlp.url:https://da-nlp-dev.inner.chj.cloud/hanlp/dep?text=}")
    private String daNlpUrl;

    private boolean nextNoFlag = false;

    @Override
    protected WordSyntaxVo execProcess(WordSyntaxVo wordSyntaxVo) {
        nextNoFlag = false;
        wordSyntaxVo = textParse(wordSyntaxVo);
        return wordSyntaxVo;
    }

    @Autowired
    private RestTemplate restTemplate;
    @Autowired
    WordSyntax wordSyntax;

    public WordSyntaxVo textParse(WordSyntaxVo wordSyntaxVo) {

        String conString = "比例,占比,多少倍,百分比,几倍";
//
        String currentDatePatter = "(比例|占比|多少倍|百分比)";
        Pattern pattern = Pattern.compile(currentDatePatter);
        Matcher matcher = pattern.matcher(wordSyntaxVo.getOriginalText());

        if (!matcher.find()) {
            return wordSyntaxVo;
        }
        if (matcher.group().equals("百分比")) {
            wordSyntaxVo.setFormatType("ratio");
        }

        Map<String, WordSyntaxVo.WordItem> mapWord = wordSyntaxVo.getWordItemList().stream().collect(Collectors.toMap(WordSyntaxVo.WordItem::getMatchText, item -> item, (ex, re) -> ex));
        CompareVo compareVo = getCompareCon(wordSyntaxVo.getOriginalText(), wordSyntaxVo, conString);

        // 比例处理
        getRatioSonCon(wordSyntaxVo, compareVo, mapWord);
        getMultipleSonCon(wordSyntaxVo, compareVo, mapWord);

        // 倍数处理
        return wordSyntaxVo;
    }


    WordSyntaxVo getMultipleSonCon(WordSyntaxVo wordSyntaxVo, CompareVo compareVo, Map<String, WordSyntaxVo.WordItem> mapWord) {


        // 如果只存在一个指标，分母的条件 与 分子的条件 应该归属同一个维度 eg：近一年北京suv的挂牌量是mpv的多少倍
        // 如果存在两个指标，分母的条件 与 分子条件 可能不归属同一个维度 eg：近一年suv的挂牌量是北京挂牌量的多少倍

        if (!wordSyntaxVo.getOriginalText().contains("多少倍")
                && !wordSyntaxVo.getOriginalText().contains("几倍")) {
            return wordSyntaxVo;
        }
        List<String> conStringList = Arrays.asList("多少倍", "几倍");
        Integer keyIndex = null;
        List<Integer> keyMeasIndexList = new ArrayList<>();

        Map<Integer, String> measNumMap = new LinkedHashMap<>();
        // 先找分母
        for (int i = 0; i < compareVo.getData().size(); i++) {
            List<String> stringList = compareVo.getData().get(i);
            for (int j = 0; j < stringList.size(); j++) {
                if (conStringList.contains(stringList.get(j))) {
                    keyIndex = i;
                }
                if (null != mapWord.get(stringList.get(j))) {
                    WordSyntaxVo.WordItem wordItem = mapWord.get(stringList.get(j));
                    if (null != wordItem.getMatchType() && wordItem.getMatchType().equals("measure")) {
                        // 如果大于两个指标，不支持比对
                        if (keyMeasIndexList.size() >= 2) {
                            log.info("sub stringList is{}", stringList);
                            continue;
                        }
                        keyMeasIndexList.add(j);

                        measNumMap.put(i, stringList.get(j));
                    }
                }

            }
        }

        List<WordSyntaxVo.SubCondition> conditionList = new ArrayList<>();

        if (keyIndex != null) {

            List<Integer> measNumKeys = new ArrayList<>(measNumMap.keySet());

            Collections.reverse(measNumKeys);

            for (Integer measNum : measNumKeys) {
                WordSyntaxVo.SubCondition subCondition = getConList(compareVo, mapWord, measNum + "~" + measNumMap.get(measNum), keyIndex);
                if (null != subCondition && !subCondition.getSonTextList().isEmpty() && null != subCondition.getSubIndex()) {
                    keyIndex = subCondition.getSubIndex() - 1;
                }
                conditionList.add(subCondition);
            }

        }

        if (conditionList.size() == 1) {
            // 补齐缺失的条件
            WordSyntaxVo.SubCondition subCondition = getConList(compareVo, mapWord, conditionList.get(0).getUniqueKey(), conditionList.get(0).getSubIndex() - 1);
            conditionList.add(subCondition);
        }

        wordSyntaxVo.setDenominator(conditionList.get(0));
        wordSyntaxVo.setNumerator(conditionList.get(1));


        return wordSyntaxVo;
    }


    WordSyntaxVo.SubCondition getConList(CompareVo compareVo, Map<String, WordSyntaxVo.WordItem> mapWord, String uniqueKey, Integer findIndex) {
        WordSyntaxVo.SubCondition condition = new WordSyntaxVo.SubCondition();
        for (int i = findIndex; i > 0; i--) {
            List<String> stringList = compareVo.getData().get(i);
            List<String> whereString = new ArrayList<>();

            for (int j = 0; j < stringList.size(); j++) {
                String stringInfo = stringList.get(j);
                // 如果只有条件，设置为分子的条件
                // 如果包含条件+指标，将第一个条件作为分子，其余条件作为可能
                // 如果已经识别为指标，同时也是条件，不做处理

                if (null != mapWord.get(stringInfo)) {
                    WordSyntaxVo.WordItem wordItem = mapWord.get(stringInfo);
                    if (null != wordItem.getSqlType() && wordItem.getSqlType().equals("where")) {
                        whereString.add(stringInfo);
                    }
                }
            }

            if (!whereString.isEmpty()) {
                condition.setUniqueKey(uniqueKey);
                condition.getSonTextList().addAll(whereString);
                condition.setSubIndex(i);
                break;
            }
        }

        return condition;
    }

    WordSyntaxVo getRatioSonCon(WordSyntaxVo wordSyntaxVo, CompareVo compareVo, Map<String, WordSyntaxVo.WordItem> mapWord) {
        if (!wordSyntaxVo.getOriginalText().contains("比例")
                && !wordSyntaxVo.getOriginalText().contains("占比")
                && !wordSyntaxVo.getOriginalText().contains("百分比")) {
            return wordSyntaxVo;
        }
        List<String> conStringList = Arrays.asList("比例", "占比", "百分比");
        Integer keyIndex = null;
        List<Integer> keyMeasIndexList = new ArrayList<>();

        List<String> measList = new ArrayList<>();

        WordSyntaxVo.SubCondition condition = new WordSyntaxVo.SubCondition();
        for (int i = 0; i < compareVo.getData().size(); i++) {
            List<String> stringList = compareVo.getData().get(i);
            for (int j = 0; j < stringList.size(); j++) {
                if (conStringList.contains(stringList.get(j))) {
                    keyIndex = i;
                }
                if (null != mapWord.get(stringList.get(j))) {
                    WordSyntaxVo.WordItem wordItem = mapWord.get(stringList.get(j));
                    if (null != wordItem.getMatchType() && wordItem.getMatchType().equals("measure")) {
                        measList.add(stringList.get(j));
                        keyMeasIndexList.add(j);
                    }
                }

            }
        }
        if (keyIndex != null) {
            for (int i = keyIndex; i > 0; i--) {
                List<String> stringList = compareVo.getData().get(i);
                List<String> whereString = new ArrayList<>();
                if (!wordSyntaxVo.getNumerator().getSonTextList().isEmpty()) {
                    break;
                }
                for (int j = 0; j < stringList.size(); j++) {
                    String stringInfo = stringList.get(j);
                    // 如果只有条件，设置为分子的条件
                    // 如果包含条件+指标，将第一个条件作为分子，其余条件作为可能
                    // 如果已经识别为指标，同时也是条件，不做处理

                    if (null != mapWord.get(stringInfo)) {
                        WordSyntaxVo.WordItem wordItem = mapWord.get(stringInfo);
                        if (null != wordItem.getSqlType() && wordItem.getSqlType().equals("where")) {
                            whereString.add(stringInfo);
                        }
                    }
                }

                if (!whereString.isEmpty()) {
                    condition.setUniqueKey(keyMeasIndexList.get(keyMeasIndexList.size() - 1) + "~" + measList.get(measList.size() - 1));
                    condition.getSonTextList().addAll(whereString);
                    wordSyntaxVo.setNumerator(condition);
                }
            }

        }
        if (null != wordSyntaxVo.getNumerator().getUniqueKey()) {
            wordSyntaxVo.getDenominator().setUniqueKey(wordSyntaxVo.getNumerator().getUniqueKey());
        }
        return wordSyntaxVo;
    }


    public CompareVo getCompareCon(String wordText, WordSyntaxVo wordSyntaxVo, String conString) {
        HttpHeaders headers = new HttpHeaders();
        headers.add("Content-Type", "application/json");
        String url = "http://10.121.156.81:5100/hanlp/con?text=" + wordText;
        url += "&dic=" + conString;
        if (wordSyntaxVo.getMatchTextList() != null && !wordSyntaxVo.getMatchTextList().isEmpty()) {
            url += "," + wordSyntaxVo.getMatchTextList().stream().collect(Collectors.joining(","));
        }
        CompareVo compareVo = restTemplate.getForObject(url, CompareVo.class);

        if (compareVo == null || !compareVo.isSuccess()) {
            throw new RuntimeException("请求" + url + "接口异常:");
        }

        return compareVo;
    }


}
