package com.graphinsight.indicator.service.wordNlp.chain;


import com.graphinsight.indicator.auto.entity.WordInfos;
import com.graphinsight.indicator.auto.entity.WordValues;
import com.graphinsight.indicator.auto.mapper.WordInfosMapper;
import com.graphinsight.indicator.auto.mapper.WordValuesMapper;
import com.graphinsight.indicator.model.vo.WordSyntaxVo;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 *
 */
@Slf4j
@Component
public class WordStandChain extends AbstractWordChain {

    /*
     * 问题：北京市净锁单量最高的3个月
     * 问题：最近3个月，北京市的净锁单量是多少？
     * 问题：今年1月和3月北京的销量是多少？
     * 问题：今年1月到3月北京的销量是多少？
     * 词语标准化处理
     * 根据预制的词典，进行拆词
     * 识别出指标，维度，时间等词汇
     * [
     *    {
     *           "word": "净锁单量",
     *        "type": "indicator",
     *        "index": 0,
     *        "start": 0,
     *        "end": 2,
     *        "wordProto": "销量"
     *        "wordType": "n",
     *  "isWhere":true,
     * "isLimit":true,
     * "isGroup":true,
     * "isSort":false,
     *    },
     *    {
     *        "word": "北京市",
     *        "type": "dimension",
     *        "start": 3,
     *        "end": 4,
     *        "wordProto": "北京"
     *        "wordType": "n"
     *    },
     *    {
     *        "word": "今年", 2024
     *        "type": "time",
     *        "start": 5,
     *        "end": 6,
     *        "wordProto": "今年"
     *        "wordType": "n"
     *    },
     *    {
     *        "word": "1月",  2021-01
     *        "type": "time",
     *        "start": 5,
     *        "end": 6,
     *        "wordProto": "1月",
     *       "wordType": "n"
     *
     *    },
     *    {
     *        "word": "和",
     *        "type": "adv",
     *        "start": 5,
     *        "end": 6
     *    },
     *    {
     *        "word": "2月", 2021-02
     *        "type": "time",
     *        "start": 8,
     *        "end": 9,
     *        "wordProto": "2月",
     *       "wordType": "n"
     *    },
     *    {
     *        "word": "最高",
     *        "type": "sort",
     *        "start": 8,
     *        "end": 9,
     *        "wordProto": "top|最高|",
     *       "wordType": "adv"
     *    }·
     * ]
     */


    @Autowired
    private WordValuesMapper wordValuesMapper;
    @Autowired
    private WordInfosMapper wordInfosMapper;

    public WordSyntaxVo execProcess(WordSyntaxVo wordSyntaxVo) {
//        List<WordValues> wordValuesList = wordValuesMapper.selectKeyList(wordSyntaxVo.getWordItemList().stream().map(WordSyntaxVo.WordItem::getMatchText).collect(Collectors.toList()));
//
//        Map<String, WordValues> wordValuesMap = wordValuesList.stream().collect(Collectors.toMap(WordValues::getValue, Function.identity(), (ex, re) -> ex));
//
//        List<WordInfos> wordInfosList = wordInfosMapper.selectKeyList(wordSyntaxVo.getWordItemList().stream().map(WordSyntaxVo.WordItem::getMatchText).collect(Collectors.toList()));
//
//        Map<String, WordInfos> wordInfosMap = wordInfosList.stream().collect(Collectors.toMap(WordInfos::getOriginalValue, Function.identity(), (ex, re) -> ex));
//
//        for (WordSyntaxVo.WordItem wordItem : wordSyntaxVo.getWordItemList()) {
//            if (wordValuesMap.get(wordItem.getMatchText()) != null) {
//                wordItem.setStandText(wordValuesMap.get(wordItem.getMatchText()).getKey());
//            }
//            if (wordInfosMap.get(wordItem.getMatchText()) != null) {
//                wordItem.setStandText(wordInfosMap.get(wordItem.getMatchText()).getUseValue());
//            }
//        }

        return wordSyntaxVo;

    }


}
