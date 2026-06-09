package com.graphinsight.indicator.service.wordNlp.chain;


import com.graphinsight.indicator.model.vo.WordSyntaxVo;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 *
 */
@Slf4j
@Component
public class WordWhereChain extends AbstractWordChain {

    @Override
    protected WordSyntaxVo execProcess(WordSyntaxVo wordSyntaxVo) {
        return whereMark(wordSyntaxVo);
    }

    // 指标 维度 维值
    // 北京的pv
    // 试驾部门的pv
    public WordSyntaxVo whereMark(WordSyntaxVo wordSyntaxVo) {


        for (int i = wordSyntaxVo.getWordItemList().size() - 1; i >= 0; i--) {
            WordSyntaxVo.WordItem wordItemInfo = wordSyntaxVo.getWordItemList().get(i);

            if (Objects.equals(wordItemInfo.getSqlType(), "where") && Objects.equals(wordItemInfo.getWordType(), "singleOp")) {
                // 向上查找
                for (int j = i - 1; j >= 0; j--) {
                    WordSyntaxVo.WordItem wordItemInfoPre = wordSyntaxVo.getWordItemList().get(j);
                    if (Objects.equals(wordItemInfoPre.getMatchType(), "measure")) {
                        wordSyntaxVo.getWordItemList().get(j).getValueList().addAll(wordItemInfo.getValueList());
                        wordSyntaxVo.getWordItemList().get(j).setWordType("singleOp");
                        wordSyntaxVo.getWordItemList().get(j).setValueType(wordItemInfo.getValueType());
                        wordSyntaxVo.getWordItemList().remove(i);
                        break;
                    }
                }
            }
            // 设置上valueType
            if (Objects.equals(wordItemInfo.getSqlType(), "where") && Objects.equals(wordItemInfo.getWordType(), "whereOp")) {
                // 向下查找，找到第一个是维值的
                int k = i + 1;
                while (true) {
                    if (k <= wordSyntaxVo.getWordItemList().size()) {
                        WordSyntaxVo.WordItem wordItemInfoNext = wordSyntaxVo.getWordItemList().get(k);
                        if (wordItemInfoNext.getMatchType().equals("dimValue")) {
                            wordSyntaxVo.getWordItemList().get(k).setValueType(wordItemInfo.getValueType());
                            //wordSyntaxVo.getWordItemList().remove(i);
                            break;
                        } else {
                            k++;
                        }
                    }

                }
            }

            if (!wordItemInfo.getBoolSet().isEmpty() && null == wordItemInfo.getSqlType() && !wordItemInfo.getValueList().isEmpty()) {
                wordItemInfo.setSqlType("where");
            }


        }
        return wordSyntaxVo;
    }

}

