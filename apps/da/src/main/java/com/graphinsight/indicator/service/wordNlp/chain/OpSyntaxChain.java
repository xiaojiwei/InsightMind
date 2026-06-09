package com.graphinsight.indicator.service.wordNlp.chain;


import com.graphinsight.indicator.constant.CommonConstants;
import com.graphinsight.indicator.enums.SqlOprType;
import com.graphinsight.indicator.model.vo.WordSyntaxVo;
import com.graphinsight.indicator.util.StringUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 *
 */
@Slf4j
@Component
public class OpSyntaxChain extends AbstractWordChain {
    private boolean nextNoFlag = false;

    @Override
    protected WordSyntaxVo execProcess(WordSyntaxVo wordSyntaxVo) {
        nextNoFlag = false;
        singleNoOp(wordSyntaxVo);
        singleSmallerOp(wordSyntaxVo);
        singleGreaterOp(wordSyntaxVo);
        return wordSyntaxVo;
    }


    public WordSyntaxVo singleNoOp(WordSyntaxVo wordSyntaxVo) {

        wordSyntaxVo.setOriginalText(wordSyntaxVo.getOriginalText());

        String currentDatePatter = "(不等于|不是|不包含|不包括|不含有|不含)";
        Pattern pattern = Pattern.compile(currentDatePatter);
        Matcher matcher = pattern.matcher(wordSyntaxVo.getOriginalText());
        while (matcher.find()) {
            log.info("match singleNoOp is {} ", matcher.group(1));
            Integer startIndex = matcher.start();
            // 定义日期格式
            // 计算指定数量之前的日期范围
            String valueType = SqlOprType.NOTIN.getDesc();
            WordSyntaxVo.WordItem wordItem = new WordSyntaxVo.WordItem();
            String matchText = matcher.group(0);
            wordItem.setMatchText(matchText);
            wordItem.setStandText(matchText);
            wordSyntaxVo.getMatchTextList().add(matchText);
            wordItem.setWordType("whereOp");
            wordItem.setMatchType("whereOp");
            wordItem.setMatchMethod("reg");
            wordItem.setOrderNum(startIndex);
            wordItem.setValueType(valueType);
            wordItem.setSqlType("where");
            wordSyntaxVo.getWordItemList().add(wordItem);
        }
        return wordSyntaxVo;
    }

    public WordSyntaxVo singleSmallerOp(WordSyntaxVo wordSyntaxVo) {

        String currentDatePatter = "(不大于等于|小于等于|不大于|不超过|不高于|小于|低于|接近)([" + CommonConstants.LARGE_NUMERALS + "]+|\\d+|[零壹贰叁肆伍陆柒捌玖]+)(万|百万|千万|亿|百亿|千亿|w|W|M|m)?";
        Pattern pattern = Pattern.compile(currentDatePatter);
        Matcher matcher = pattern.matcher(wordSyntaxVo.getOriginalText());
        Integer startIndex = null;
        while (matcher.find()) {
            log.info("match singleOp is {} ", matcher.group(1) + matcher.group(2) + matcher.group(3));
            startIndex = matcher.start();
            // 定义日期格式
            // 计算指定数量之前的日期范围
            String valueType = "";
            switch (matcher.group(1)) {
                case "不大于等于":
                case "小于":
                case "低于":
                case "接近":
                    valueType = SqlOprType.SMALLER_THAN.getDesc();
                    break;
                case "不大于":
                case "小于等于":
                case "不高于":
                    valueType = SqlOprType.SMALLER_THAN_OR_EQUAL.getDesc();
                    break;
                default:
                    break;

            }
            WordSyntaxVo.WordItem wordItem = new WordSyntaxVo.WordItem();
            String valueStr = matcher.group(2);
            if (null != matcher.group(3)) {
                valueStr += matcher.group(3);
                Integer valueNum = StringUtil.getDateBeforeNumber(valueStr);
                if (null != valueNum) {
                    switch (matcher.group(3)) {
                        case "m":
                        case "M":
                        case "w":
                        case "W":
                        case "万":
                            valueNum = valueNum * 10000;
                            break;
                        case "百万":
                            valueNum = valueNum * 1000000;
                            break;
                    }
                    wordItem.getValueList().add(valueNum.toString());
                }
            } else {
                wordItem.getValueList().add(valueStr);
            }

            String matchText = matcher.group(0);
            wordItem.setMatchText(matchText);
            wordItem.setStandText(matchText);
            wordSyntaxVo.getMatchTextList().add(matchText);
            wordItem.setWordType("singleOp");
            wordItem.setMatchType("singleOp");
            wordItem.setMatchMethod("reg");
            wordItem.setOrderNum(startIndex);
            wordItem.setValueType(valueType);
            wordItem.setSqlType("where");
            // 剔除当前匹配到的的
            String originalText = wordSyntaxVo.getOriginalText();
            originalText = originalText.substring(0, startIndex) + originalText.substring(startIndex + matchText.length());
            wordSyntaxVo.setOriginalText(originalText);
            wordSyntaxVo.getWordItemList().add(wordItem);
        }
        return wordSyntaxVo;
    }


    public WordSyntaxVo singleGreaterOp(WordSyntaxVo wordSyntaxVo) {

        String currentDatePatter = "(不小于等于|大于等于|不小于|不低于|高于|超过|大于)([" + CommonConstants.LARGE_NUMERALS + "]+|\\d+|[零壹贰叁肆伍陆柒捌玖]+)(万|百万|千万|亿|百亿|千亿|w|W|M|m)?";
        Pattern pattern = Pattern.compile(currentDatePatter);
        Matcher matcher = pattern.matcher(wordSyntaxVo.getOriginalText());
        Integer startIndex = null;
        while (matcher.find()) {
            log.info("match singleOp is {} ", matcher.group(1) + matcher.group(2) + matcher.group(3));
            startIndex = matcher.start();
            // 定义日期格式
            // 计算指定数量之前的日期范围
            String valueType = "";
            switch (matcher.group(1)) {
                case "不小于等于":
                case "大于":
                case "超过":
                case "高于":
                    valueType = SqlOprType.GREATER_THAN.getDesc();
                    break;
                case "大于等于":
                case "不小于":
                case "不低于":
                    valueType = SqlOprType.GREATER_THAN_OR_EQUAL.getDesc();
                    break;
                default:
                    break;

            }
            WordSyntaxVo.WordItem wordItem = new WordSyntaxVo.WordItem();
            String valueStr = matcher.group(2);
            if (null != matcher.group(3)) {
                valueStr += matcher.group(3);
                Integer valueNum = StringUtil.getDateBeforeNumber(valueStr);
                if (null != valueNum) {
                    switch (matcher.group(3)) {
                        case "m":
                        case "M":
                        case "w":
                        case "W":
                        case "万":
                            valueNum = valueNum * 10000;
                            break;
                        case "百万":
                            valueNum = valueNum * 1000000;
                            break;
                    }
                    wordItem.getValueList().add(valueNum.toString());
                }
            } else {
                wordItem.getValueList().add(valueStr);
            }
            String matchText = matcher.group(0);
            wordItem.setMatchText(matchText);
            wordItem.setStandText(matchText);
            wordSyntaxVo.getMatchTextList().add(matchText);
            wordItem.setWordType("singleOp");
            wordItem.setMatchType("singleOp");
            wordItem.setMatchMethod("reg");
            wordItem.setOrderNum(startIndex);
            wordItem.setValueType(valueType);
            wordItem.setSqlType("where");
            String originalText = wordSyntaxVo.getOriginalText();
            originalText = originalText.substring(0, startIndex) + originalText.substring(startIndex + matchText.length());
            wordSyntaxVo.setOriginalText(originalText);
            wordSyntaxVo.getWordItemList().add(wordItem);
        }
        return wordSyntaxVo;
    }


}

