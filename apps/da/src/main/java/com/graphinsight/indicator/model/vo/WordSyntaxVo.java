package com.graphinsight.indicator.model.vo;

import com.graphinsight.indicator.auto.entity.AiSearchInfo;
import io.swagger.models.auth.In;
import lombok.Data;

import java.util.*;

/**
 * Author: lixiaolong
 * Date: 2022/11/28
 * Desc: 授权对象
 */
@Data
public class WordSyntaxVo {

    // 最原始的文本内容
    private String rootText;
    // 文本替换之后的内容
    private String originalText;
    // 拆词的文档内容
    private String splitText;

    private Integer measNum;

    private List<WordItem> wordItemList = new ArrayList<>();

    // 血缘信息
    private RelatedSet relatedSet = new RelatedSet();

    // 血缘信息
    private List<String> matchTextList = new ArrayList<>();

    private String formatType;
    private SubCondition numerator = new SubCondition();
    private SubCondition denominator = new SubCondition();

    private Boolean isBoard = false;

    private Boolean natureRatioFlag = false;
    private SubCompareRatio compareRatio = new SubCompareRatio();

    @Data
    public static class SubCompareRatio {
        private String uniqueKey;
        private String subDate;
    }

    @Data
    public static class SubCondition {
        // orderNum + '~' + wordName 组成唯一标识
        private String uniqueKey;
        private List<String> sonTextList = new ArrayList<>();
        private Integer subIndex;

    }

    @Data
    public static class WordItem {

        // 最原始的文本内容
        private String originalText;

        // 处理后的标准内容
        private String standText;

        // 匹配到的文本内容的
        private String matchText;

        // 处理文本内容出现的位置
        private Integer orderNum;

        // 识别到的文本词性
        private String wordType;

        // 匹配到的文本内容的方式 人工正则 正常分词
        private String matchMethod;

        // 匹配到文本句式类型
        private String matchType;
        // 识别到的sql类型
        private String sqlType;
        // 识别到的文本词性排序类型
        private String orderType;
        // 识别到的值类型 range single
        private String valueType;

        // 识别到的值内容
        private List<String> valueList = new ArrayList<>();

        private Map<String, String> valueMap = new HashMap<>();

        // 识别到的维度值内容
        private List<String> dimValueList = new ArrayList<>();

        // 识别到的血缘
        private Set<Integer> boolSet = new HashSet<>();

        private Set<Integer> boolLikeSet = new HashSet<>();

        // 识别到的指标表达式
        private Map<String, List<String>> calMeas = new HashMap<>();
    }
}
