package com.graphinsight.indicator.service.wordNlp.chain;


import com.graphinsight.indicator.auto.entity.WordValues;
import com.graphinsight.indicator.auto.mapper.WordValuesMapper;
import com.graphinsight.indicator.model.vo.WordSyntaxVo;
import lombok.extern.slf4j.Slf4j;
import org.ansj.domain.Result;
import org.ansj.domain.Term;
import org.ansj.splitWord.analysis.DicAnalysis;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.stream.Collectors;

import static com.graphinsight.indicator.service.wordNlp.WordDictService.FOREST;

/**
 *
 */
@Slf4j
@Component
public class WordReplaceChain extends AbstractWordChain {


    @Autowired
    WordValuesMapper wordValuesMapper;

    // 全局词语替换，注意循环
    public WordSyntaxVo execProcess(WordSyntaxVo wordSyntaxVo) {

        List<WordValues> wordValueList = wordValuesMapper.selectInfoList();
        String info = wordSyntaxVo.getRootText();
        for (WordValues wordValue : wordValueList) {
            if (wordValue.getType() == 1) {
                if (info.contains(wordValue.getValue())) {
                    if (wordValue.getKey().contains("|")) {
                        info = info.replace(wordValue.getValue(), wordValue.getKey().replace("|", ","));
                    } else {
                        info = info.replace(wordValue.getValue(), wordValue.getKey());
                    }
                }
            } else {
                if (info.contains(wordValue.getValue()) && !info.contains(wordValue.getKey())) {
                    info = info.replace(wordValue.getValue(), wordValue.getKey());
                }
            }


        }
        wordSyntaxVo.setOriginalText(info);
        return wordSyntaxVo;
    }


}

