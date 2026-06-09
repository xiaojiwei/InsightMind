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
public class WordGroupChain extends AbstractWordChain {

    @Override
    protected WordSyntaxVo execProcess(WordSyntaxVo wordSyntaxVo) {
        return groupMark(wordSyntaxVo);
    }

    // 指标 维度 维值
    // 北京的pv
    // 试驾部门的pv
    public WordSyntaxVo groupMark(WordSyntaxVo wordSyntaxVo) {

        List<String> queryList = new ArrayList<>();
        for (WordSyntaxVo.WordItem wordItem : wordSyntaxVo.getWordItemList()) {
            if (Objects.equals(wordItem.getWordType(), "uj") || wordItem.getStandText() != null) {
                continue;
            }
            queryList.add(wordItem.getMatchText());
        }

        Map<String, String> groupWordKeys = new HashMap<>();
        groupWordKeys.put("各个", "各个");
        groupWordKeys.put("每个", "每个");
        groupWordKeys.put("不同", "不同");
        groupWordKeys.put("不同的", "不同的");
        groupWordKeys.put("按", "按");
        groupWordKeys.put("按照", "按照");
        groupWordKeys.put("分", "分");
        groupWordKeys.put("各", "各");

        Map<String, WordSyntaxVo.WordItem> wordOrderItemMap = new LinkedHashMap<>();
        for (int i = 0; i < wordSyntaxVo.getWordItemList().size(); i++) {

            WordSyntaxVo.WordItem wordItem = wordSyntaxVo.getWordItemList().get(i);
            if (Objects.equals(wordItem.getWordType(), "uj") || wordItem.getSqlType() != null) {
                continue;
            }

            // 将后面的一个识别到的词处理为维度
            if (null != groupWordKeys.get(wordItem.getMatchText())) {
                if (i + 1 < wordSyntaxVo.getWordItemList().size()) {
                    WordSyntaxVo.WordItem wordItemDim = wordSyntaxVo.getWordItemList().get(i + 1);
                    if (wordItemDim != null && !wordItemDim.getBoolSet().isEmpty()) {
                        wordItemDim.setSqlType("group");
                    }
                    if (i + 2 < wordSyntaxVo.getWordItemList().size()) {
                        WordSyntaxVo.WordItem wordItemDimNextTwo = wordSyntaxVo.getWordItemList().get(i + 2);
                        if (Objects.equals(wordItemDimNextTwo.getMatchText(), "和")
                                || Objects.equals(wordItemDimNextTwo.getMatchText(), "与")
                                || Objects.equals(wordItemDimNextTwo.getMatchText(), "、")) {
                            if (i + 3 < wordSyntaxVo.getWordItemList().size()) {
                                WordSyntaxVo.WordItem wordItemDimNextThree = wordSyntaxVo.getWordItemList().get(i + 3);
                                if (wordItemDimNextThree != null && !wordItemDimNextThree.getBoolSet().isEmpty()) {
                                    wordItemDimNextThree.setSqlType("group");
                                }
                            }
                        }
                    }
                }

            }

        }

        // 识别的维度排序设置
        for (int i = 0; i < wordSyntaxVo.getWordItemList().size(); i++) {

            WordSyntaxVo.WordItem wordItem = wordSyntaxVo.getWordItemList().get(i);
            if (Objects.equals(wordItem.getWordType(), "uj") ||
                    wordItem.getValueType() == null) {
                continue;
            }

            int orderLimit = 0;
            // 先上查找到 按 按照 3次字段 这些字
            for (int j = i - 1; j < i && j > 0; j--) {
                if (orderLimit >= 1) {
                    break;
                }
                WordSyntaxVo.WordItem wordOrderItem = wordSyntaxVo.getWordItemList().get(j);
                if (Objects.equals(wordOrderItem.getMatchText(), "按") || Objects.equals(wordOrderItem.getMatchText(), "按照")) {
                    break;
                }
                if (!wordOrderItem.getBoolSet().isEmpty()) {
                    orderLimit++;
                    wordOrderItem.setOrderType(wordItem.getValueType());
                }
            }


        }

        for (int i = 0; i < wordSyntaxVo.getWordItemList().size(); i++) {

            WordSyntaxVo.WordItem wordItem = wordSyntaxVo.getWordItemList().get(i);
            if (Objects.equals(wordItem.getWordType(), "uj")) {
                continue;
            }
            if (!wordItem.getBoolSet().isEmpty()
                    && !Objects.equals(wordItem.getSqlType(), "where")
                    && Objects.equals(wordItem.getMatchType(), "dimension")) {
                wordItem.setSqlType("group");
            }
        }

        return wordSyntaxVo;

    }


}

