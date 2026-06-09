package com.graphinsight.indicator.service.wordNlp.chain;

import com.graphinsight.indicator.model.vo.WordSyntaxVo;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
@Slf4j
public class GroupSyntaxChain extends AbstractWordChain {
    private boolean nextNoFlag = false;
    @Override
    protected WordSyntaxVo execProcess(WordSyntaxVo wordSyntaxVo) {
        nextNoFlag = false;
        wordSyntaxVo = dateTrendGroup(wordSyntaxVo);
        wordSyntaxVo = dateGroup(wordSyntaxVo);

        return wordSyntaxVo;
    }
    public WordSyntaxVo dateTrendGroup(WordSyntaxVo wordSyntaxVo) {

        if (nextNoFlag) {
            return wordSyntaxVo;
        }
        String currentDatePatter = "(月趋势|年趋势|日趋势)";
        Pattern pattern = Pattern.compile(currentDatePatter);
        Matcher matcher = pattern.matcher(wordSyntaxVo.getOriginalText());
        if (matcher.find()) {
            log.info("match order is {} ", matcher.group(1));

            // 根据时间单位判断指定类型
            Integer startIndex = matcher.start();

            WordSyntaxVo.WordItem wordItem = new WordSyntaxVo.WordItem();
            wordItem.setOrderNum(startIndex);
            String matchText = matcher.group();
            log.info("match text is {}", matchText);

            wordItem.setMatchText(matchText);
            wordSyntaxVo.getMatchTextList().add(matchText);
            wordItem.setStandText(matchText);
            wordItem.setMatchMethod("reg");
            wordItem.setMatchType("date");
            wordItem.setSqlType("group");
            switch (matcher.group(1)) {
                case "年":
                case "年份":
                case "年趋势":
                    wordItem.setWordType("year");
                    wordItem.setOrderType("asc");
                    break;
                case "月":
                case "月份":
                case "月趋势":
                    wordItem.setWordType("month");
                    wordItem.setOrderType("asc");
                    break;
                case "周":
                case "季度":
                case "日":
                case "天":
                case "日趋势":
                    wordItem.setWordType("day");
                    wordItem.setOrderType("asc");
                    break;
            }
            nextNoFlag = true;
            wordSyntaxVo.getWordItemList().add(wordItem);
        }

        return wordSyntaxVo;
    }

    // 销量最高的3个月 取后面的作为order
    public WordSyntaxVo dateGroup(WordSyntaxVo wordSyntaxVo) {
        if (nextNoFlag) {
            return wordSyntaxVo;
        }

        String currentDatePatter = "(按|按照|每|每个|各|各个|分)(月份|年份|季度|季|周|年|月|日|天)";
        Pattern pattern = Pattern.compile(currentDatePatter);
        Matcher matcher = pattern.matcher(wordSyntaxVo.getOriginalText());
        if (matcher.find()) {
            log.info("match order is {} ", matcher.group(1) + matcher.group(2));

            // 根据时间单位判断指定类型
            Integer startIndex = matcher.start();

            WordSyntaxVo.WordItem wordItem = new WordSyntaxVo.WordItem();
            wordItem.setOrderNum(startIndex);
            String matchText = matcher.group();
            log.info("match text is {}", matchText);

            wordItem.setMatchText(matchText);
            wordSyntaxVo.getMatchTextList().add(matchText);
            wordItem.setStandText(matchText);
            wordItem.setMatchMethod("reg");
            wordItem.setMatchType("date");
            wordItem.setSqlType("group");
            wordItem.setValueList(Collections.singletonList(matcher.group(2)));
            switch (matcher.group(2)) {
                case "年":
                case "年份":
                case "年趋势":
                    wordItem.setWordType("year");
                    wordItem.setOrderType("asc");
                    break;
                case "月":
                case "月份":
                case "月趋势":
                    wordItem.setWordType("month");
                    wordItem.setOrderType("asc");
                    break;
                case "周":
                case "季度":
                case "日":
                case "天":
                case "日趋势":
                    wordItem.setWordType("day");
                    wordItem.setOrderType("asc");
                    break;
            }
            nextNoFlag = true;
            wordSyntaxVo.getWordItemList().add(wordItem);
        }

        return wordSyntaxVo;
    }
}
