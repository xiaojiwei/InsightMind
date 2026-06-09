package com.graphinsight.indicator.util;

import com.hankcs.hanlp.HanLP;
import com.hankcs.hanlp.dictionary.CustomDictionary;

import java.util.*;
import java.util.stream.Collectors;

public class SimilarityUtil {
    private static final String FILTER_TERMS = "`~!@#$^&*()=|{}':;',\\[\\].<>/?~！@#￥……&*（）——|{}【】‘；：”“'。，、？";

    static {
        // HanLP 用户自定义词典
        CustomDictionary.add("P6");
        CustomDictionary.add("P7");
    }

    private SimilarityUtil() {
    }

    /**
     * 相似性比较

     * @param sentence1 句子1
     * @param sentence2 句子2
     * @return
     */
    public static double getSimilarity(String sentence1, String sentence2) {
        List<String> sent1Words = getSplitWords(sentence1);
        List<String> sent2Words = getSplitWords(sentence2);
        List<String> allWords = mergeList(sent1Words, sent2Words);

        int[] statistic1 = statistic(allWords, sent1Words);
        int[] statistic2 = statistic(allWords, sent2Words);

        double dividend = 0;
        double divisor1 = 0;
        double divisor2 = 0;
        for (int i = 0; i < statistic1.length; i++) {
            dividend += statistic1[i] * statistic2[i];
            divisor1 += Math.pow(statistic1[i], 2);
            divisor2 += Math.pow(statistic2[i], 2);
        }

        return dividend / (Math.sqrt(divisor1) * Math.sqrt(divisor2));
    }

    /**
     * 词频分析
     *
     * @param allWords  去重后所有词列表
     * @param sentWords 分析词列表
     * @return
     */
    private static int[] statistic(List<String> allWords, List<String> sentWords) {
        int[] result = new int[allWords.size()];
        for (int i = 0; i < allWords.size(); i++) {
            result[i] = Collections.frequency(sentWords, allWords.get(i));
        }
        return result;
    }

    /**
     * 合并两个分词结果（去重）
     */
    private static List<String> mergeList(List<String> list1, List<String> list2) {
        List<String> result = new ArrayList<>();
        result.addAll(list1);
        result.addAll(list2);
        return result.stream().distinct().collect(Collectors.toList());
    }

    /**
     * 分词（过滤特殊字符的词）
     */
    private static List<String> getSplitWords(String sentence) {
        return HanLP.segment(sentence).stream().map(a -> a.word).filter(s -> !FILTER_TERMS.contains(s)).collect(Collectors.toList());
    }

}
