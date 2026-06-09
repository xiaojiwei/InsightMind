package com.graphinsight.indicator.service.wordNlp.chain;


import com.graphinsight.indicator.auto.entity.WordValues;
import com.graphinsight.indicator.auto.mapper.WordValuesMapper;
import com.graphinsight.indicator.model.vo.WordSyntaxVo;
import lombok.extern.slf4j.Slf4j;
import org.ansj.domain.Result;
import org.ansj.domain.Term;
import org.ansj.splitWord.analysis.DicAnalysis;
import org.nlpcn.commons.lang.tire.domain.Value;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;

import java.util.*;
import java.util.stream.Collectors;

import static com.graphinsight.indicator.service.wordNlp.WordDictService.FOREST;

/**
 *
 */
@Slf4j
@Component
public class WordSplitChain extends AbstractWordChain {


    // 获取分词后的结果
    public WordSyntaxVo execProcess(WordSyntaxVo wordSyntaxVo) {

        String splitInfo = wordSyntaxVo.getOriginalText();

        for (String matchTextItem : wordSyntaxVo.getMatchTextList()) {
            splitInfo = splitInfo.replace(matchTextItem, "");
        }
        Map<Integer, String> splitMap = wordSyntaxVo.getWordItemList().stream().collect(Collectors.toMap(WordSyntaxVo.WordItem::getOrderNum, WordSyntaxVo.WordItem::getMatchText));
        wordSyntaxVo.setSplitText(splitInfo);

        List<Term> termList = split(wordSyntaxVo);

        Integer offset = 0;
        for (Term term : termList) {

            WordSyntaxVo.WordItem wordItem = new WordSyntaxVo.WordItem();
            //  Integer startIndex = wordSyntaxVo.getOriginalText().toLowerCase().indexOf(term.getName());
            Integer startIndex = term.getOffe();
            if (null != splitMap.get(startIndex)) {
                offset += splitMap.get(startIndex).length();
            }
            startIndex += offset;
            // 自定义的词
            wordItem.setMatchText(term.getName());
//            wordItem.setWordType(wordList.contains(term.getName()) ? term.getName() : term.getNatureStr());
            wordItem.setMatchMethod("split");
            wordItem.setOrderNum(startIndex);
            wordItem.setWordType(term.getNatureStr());
            log.info("term is {}", term);
            wordSyntaxVo.getWordItemList().add(wordItem);
        }
        // 按照顺序排序 没有识别的不在处理
        wordSyntaxVo.getWordItemList().sort(Comparator.comparingInt(WordSyntaxVo.WordItem::getOrderNum));
        return wordSyntaxVo;
    }

    @Autowired
    private WordValuesMapper wordValuesMapper;

    // 分词
    public List<Term> split(WordSyntaxVo wordSyntaxVo) {

        Result result = DicAnalysis.parse(wordSyntaxVo.getSplitText(), FOREST);

        List<Term> termList = result.getTerms();
        if (!CollectionUtils.isEmpty(termList)) {
            for (Term term : termList) {

                List<String> values = new ArrayList<>();
                values.add(term.getName());

                List<WordValues> wordValues = wordValuesMapper.selectKeyList(values);

                if (!CollectionUtils.isEmpty(wordValues)) {
                    for (WordValues wordValue : wordValues) {
                        term.setName(wordValue.getKey());
                        break;
                    }
                }
            }
        }

        return termList;

    }

}

