package com.graphinsight.indicator.service.wordNlp.chain;

import com.graphinsight.indicator.constant.CommonConstants;
import com.graphinsight.indicator.model.vo.WordSyntaxVo;
import com.graphinsight.indicator.util.StringUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
@Slf4j
public class OrderSyntaxChain extends AbstractWordChain {

    private boolean nextNoFlag = false;

    @Override
    protected WordSyntaxVo execProcess(WordSyntaxVo wordSyntaxVo) {
        nextNoFlag = false;
        wordSyntaxVo = orderLimitBeforeSyntax(wordSyntaxVo);
        wordSyntaxVo = orderAfterSyntax(wordSyntaxVo);
        wordSyntaxVo = orderAfter2Syntax(wordSyntaxVo);
//        wordSyntaxVo = orderBeforeSyntax(wordSyntaxVo);

        return wordSyntaxVo;
    }

    public WordSyntaxVo orderLimitBeforeSyntax(WordSyntaxVo wordSyntaxVo) {
        if (nextNoFlag) {
            return wordSyntaxVo;
        }
        String currentDatePatter = "(哪个|哪|哪些|哪些个)([" + CommonConstants.LARGE_NUMERALS + "]+|\\d+|[零壹贰叁肆伍陆柒捌玖]+)?(.*)?(最小的|最小|最大的|最大|最少的|最少|最多的|最多|最高的|最高|最低的|最低|top)";
        Pattern pattern = Pattern.compile(currentDatePatter);
        Matcher matcher = pattern.matcher(wordSyntaxVo.getOriginalText());


        String valueItem = null;
        WordSyntaxVo.WordItem wordItem = new WordSyntaxVo.WordItem();
        if (matcher.find()) {
            log.info("orderAfter2Syntax match order info is {} {} {} {} ", matcher.group(1), matcher.group(2), matcher.group(3), matcher.group(4));
            Integer startIndex = matcher.start();
            if (matcher.group(2) != null) {
                valueItem = matcher.group(2);
            } else {
                valueItem = "1";
            }

            Integer valueNum = StringUtil.getDateBeforeNumber(valueItem);
            if (valueNum != null) {
                wordItem.setValueList(Collections.singletonList(valueNum.toString()));
            }
            String matchText = matcher.group(1);
            if (null != matcher.group(2)) {
                matchText += matcher.group(2);
            }
            wordItem.setMatchText(matchText);
            wordItem.setStandText(matchText);
            wordItem.setOrderNum(startIndex);
            wordItem.setMatchMethod("reg");
            wordItem.setMatchType("orderAfter");
            wordItem.setSqlType("order");
            wordItem.setWordType("order");
            wordSyntaxVo.getMatchTextList().add(matchText);
            wordSyntaxVo.getMatchTextList().add(matcher.group(4));

            switch (matcher.group(4)) {
                case "前":
                case "最高的":
                case "最大的":
                case "最大":
                case "最高":
                case "top":
                case "最多的":
                case "最多":
                    wordItem.setValueType("desc");
                    break;
                case "后":
                case "最低":
                case "最低的":
                case "最少的":
                case "最少":
                case "最小的":
                case "最小":
                    wordItem.setValueType("asc");
                    break;
            }
            wordSyntaxVo.getWordItemList().add(wordItem);


            nextNoFlag = true;
        }
//


        return wordSyntaxVo;
    }

    // 销量最高的3个月 取后面的作为order
    public WordSyntaxVo orderAfterSyntax(WordSyntaxVo wordSyntaxVo) {
        if (nextNoFlag) {
            return wordSyntaxVo;
        }
        String currentDatePatter = "(后|前|最少|最少的|最多的|最多|最高的|最高|最低的|最低|最小的|最小|最大的|最大|top)([" + CommonConstants.LARGE_NUMERALS + "]+|\\d+|[零壹贰叁肆伍陆柒捌玖]+)?(个?)";
        Pattern pattern = Pattern.compile(currentDatePatter);
        Matcher matcher = pattern.matcher(wordSyntaxVo.getOriginalText());
        if (matcher.find()) {
            log.info("orderAfterSyntax match order info is {} {} {} ", matcher.group(1), matcher.group(2), matcher.group(3));
            // 根据时间单位判断指定类型
            Integer startIndex = matcher.start();
            String matchText = matcher.group(1);
            WordSyntaxVo.WordItem wordItem = new WordSyntaxVo.WordItem();
            wordItem.setOrderNum(startIndex);
            String valueItem = "";
            if (null != matcher.group(2)) {
                matchText += matcher.group(2);
                if ("个".equals(matcher.group(3))) {
                    matchText += matcher.group(3);
                }
                valueItem = matcher.group(2);
            } else {
                valueItem = "1";
            }
            Integer valueNum = StringUtil.getDateBeforeNumber(valueItem);
            if (valueNum != null) {
                wordItem.setValueList(Collections.singletonList(valueNum.toString()));
            }
            wordItem.setMatchText(matchText);
            wordSyntaxVo.getMatchTextList().add(matchText);
            wordItem.setStandText(matchText);
            wordItem.setMatchMethod("reg");
            wordItem.setMatchType("orderAfter");
            wordItem.setSqlType("order");

            switch (matcher.group(1)) {
                case "前":
                case "最高的":
                case "最高":
                case "top":
                case "最多的":
                case "最多":
                case "最大的":
                case "最大":
                    wordItem.setValueType("desc");
                    break;
                case "后":
                case "最低":
                case "最低的":
                case "最少的":
                case "最少":
                case "最小的":
                case "最小":
                    wordItem.setValueType("asc");
                    break;
            }
            nextNoFlag = true;
            wordSyntaxVo.getWordItemList().add(wordItem);
        }

        return wordSyntaxVo;
    }


    public WordSyntaxVo orderAfter2Syntax(WordSyntaxVo wordSyntaxVo) {
        if (nextNoFlag) {
            return wordSyntaxVo;
        }
        String currentDatePatter = "(由大到小的|由大到小|从大到小的|从大到小|由小到大的|由小到大|从小到大的|从小到大|升序|降序|排序)(给出|输出)?([" + CommonConstants.LARGE_NUMERALS + "]+|\\d+|[零壹贰叁肆伍陆柒捌玖]+)?(个?)";
        Pattern pattern = Pattern.compile(currentDatePatter);
        Matcher matcher = pattern.matcher(wordSyntaxVo.getOriginalText());


        String valueItem = null;
        WordSyntaxVo.WordItem wordItem = new WordSyntaxVo.WordItem();
        if (matcher.find()) {
            log.info("orderAfter2Syntax match order info is {} {} {} {} ", matcher.group(1), matcher.group(2), matcher.group(3), matcher.group(4));
            Integer startIndex = matcher.start();
            Integer endIndex = matcher.end();

            if (matcher.group(3) != null) {
                valueItem = matcher.group(3);
                wordItem.setValueList(Collections.singletonList(valueItem));
            }
            String matchText = wordSyntaxVo.getOriginalText().substring(startIndex, endIndex);


            wordItem.setMatchText(matchText);
            wordItem.setStandText(matchText);
            wordItem.setOrderNum(startIndex);
            wordItem.setMatchMethod("reg");
            wordItem.setMatchType("orderAfter");
            wordItem.setSqlType("order");
            wordItem.setWordType("order");
            wordSyntaxVo.getMatchTextList().add(matchText);
            switch (matcher.group(1)) {
                case "由大到小":
                case "由大到小的":
                case "从大到小":
                case "从大到小的":
                case "降序":
                    wordItem.setValueType("desc");
                    break;
                case "由小到大":
                case "由小到大的":
                case "从小到大":
                case "从小到大的":
                case "升序":
                case "排序":
                    wordItem.setValueType("asc");
                    break;
            }
            wordSyntaxVo.getWordItemList().add(wordItem);


            nextNoFlag = true;
        }
//


        return wordSyntaxVo;
    }



    // 地区销量最高的top3 从后往前识别，识别到的第一个维度作为order字段
    public WordSyntaxVo orderBeforeSyntax(WordSyntaxVo wordSyntaxVo) {

        String currentDatePatter = "(最小的|最小|最大的|最大|最高|最高的|最高的top|最高的前|最低|最低的|最低的后)([" + CommonConstants.LARGE_NUMERALS + "]+|\\d+|[零壹贰叁肆伍陆柒捌玖]+)([个名])?";
        Pattern pattern = Pattern.compile(currentDatePatter);
        Matcher matcher = pattern.matcher(wordSyntaxVo.getOriginalText());
        if (matcher.find() && matcher.group().length() >= 2) {
            // 后面没有任何修辞
            if (matcher.end() == wordSyntaxVo.getOriginalText().length()) {

                log.info("match order before is {} ", matcher.group(1) + matcher.group(2));

                // 根据时间单位判断指定类型
                Integer startIndex = matcher.start();


                WordSyntaxVo.WordItem wordItem = new WordSyntaxVo.WordItem();
                wordItem.setOrderNum(startIndex);
                String matchText = matcher.group(1) + matcher.group(2);
                if ("个".equals(matcher.group(3)) || "名".equals(matcher.group(3))) {
                    matchText += matcher.group(3);
                }
                wordItem.setMatchText(matchText);
                wordItem.setStandText(matchText);
                wordItem.setMatchMethod("reg");
                wordItem.setMatchType("orderBefore");
                wordItem.setSqlType("order");
                Integer valueNum = StringUtil.getDateBeforeNumber(matcher.group(2));
                if (valueNum != null) {
                    wordItem.setValueList(Collections.singletonList(valueNum.toString()));
                }
                switch (matcher.group(1)) {
                    case "最高的top":
                    case "最高的前":
                    case "最高的":
                    case "最大的":
                    case "最大":
                        wordItem.setValueType("desc");
                        break;
                    case "最低":
                    case "最低的":
                    case "最低的后":
                    case "最小的":
                    case "最小":
                        wordItem.setValueType("asc");
                        break;
                }
                wordSyntaxVo.getWordItemList().add(wordItem);
            }
        }
        return wordSyntaxVo;
    }
}
