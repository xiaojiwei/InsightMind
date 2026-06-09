package com.graphinsight.indicator.service.wordNlp.chain;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.graphinsight.indicator.auto.entity.DimAllValuesInfo;
import com.graphinsight.indicator.auto.entity.TSpace;
import com.graphinsight.indicator.auto.entity.WordValues;
import com.graphinsight.indicator.auto.mapper.DimAllValuesMapper;
import com.graphinsight.indicator.auto.mapper.DimensionValuesMapper;
import com.graphinsight.indicator.auto.mapper.WordValuesMapper;
import com.graphinsight.indicator.constant.CommonConstants;
import com.graphinsight.indicator.model.vo.DateExtractVo;
import com.graphinsight.indicator.model.vo.TextNodeVo;
import com.graphinsight.indicator.model.vo.WordSyntaxVo;
import com.graphinsight.indicator.util.StringUtil;
import com.xkzhangsan.time.nlp.TimeNLP;
import com.xkzhangsan.time.nlp.TimeNLPUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.text.SimpleDateFormat;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Slf4j
@Component
public class DateSyntaxChain extends AbstractWordChain {
    private boolean nextNoFlag = false;

    @Autowired
    private DimAllValuesMapper dimAllValuesMapper;

    @Override
    public WordSyntaxVo execProcess(WordSyntaxVo wordSyntaxVo) {
        this.nextNoFlag = false;
        wordSyntaxVo = quarterSyntax(wordSyntaxVo);

        wordSyntaxVo = lateDateSyntax(wordSyntaxVo);

        wordSyntaxVo = natureDateSyntax(wordSyntaxVo);

        wordSyntaxVo = natureV2DateSyntax(wordSyntaxVo);

        wordSyntaxVo = curDateSyntax(wordSyntaxVo);
        return wordSyntaxVo;
    }

    public WordSyntaxVo natureDateSyntax(WordSyntaxVo wordSyntaxVo) {
        // 使用日期分词，将识别到的分词也作为字典
        if (nextNoFlag) {
            return wordSyntaxVo;
        }

        DateExtractVo dateExtractVo = getDateExtractVo(wordSyntaxVo.getOriginalText());
        if (dateExtractVo == null) {
            return wordSyntaxVo;
        }

        SimpleDateFormat formatter = null;

        WordSyntaxVo.WordItem wordItem = new WordSyntaxVo.WordItem();
        Set<String> matchList = new HashSet<>();

        List<Date> dateList = new ArrayList<>();

        Map<String, List<Date>> dateMatchMap = new HashMap<>();

        for (DateExtractVo.DateInfo dateInfo : dateExtractVo.getData()) {


            if (null == wordItem.getOrderNum()) {
                wordItem.setOrderNum(dateInfo.getOffset().get(0));
            }
            if (dateInfo.getText().contains("号") || dateInfo.getText().contains("日") || dateInfo.getText().contains("天")) {
                wordItem.setWordType("day");
                formatter = new SimpleDateFormat("yyyy-MM-dd");
            } else {
                if (dateInfo.getText().contains("月")) {
                    if (null == wordItem.getWordType()
                            || !wordItem.getWordType().equals("day")) {
                        wordItem.setWordType("month");
                        formatter = new SimpleDateFormat("yyyyMM");

                    }
                } else {
                    if (dateInfo.getText().contains("年")) {
                        if (null == wordItem.getWordType()
                                || (!wordItem.getWordType().equals("day") && !wordItem.getWordType().equals("month"))) {
                            wordItem.setWordType("year");
                            formatter = new SimpleDateFormat("yyyy");
                        }
                    }
                }
            }
            if (formatter == null) {
                break;
            }

            if (Objects.equals(dateInfo.getType(), "time_span")) {
                dateMatchMap.put(dateInfo.getText(), dateInfo.getDateTime());
                dateList.addAll(dateInfo.getDateTime());
            } else {
                if (!dateInfo.getDateTime().isEmpty()) {
                    dateMatchMap.put(dateInfo.getText(), Collections.singletonList(dateInfo.getDateTime().get(0)));
                    dateList.add(dateInfo.getDateTime().get(0));
                }

            }
        }
        if (formatter == null || dateList.isEmpty()) {
            return wordSyntaxVo;
        }
        Collections.sort(dateList);
        for (Map.Entry<String, List<Date>> entry : dateMatchMap.entrySet()) {
            wordSyntaxVo.getMatchTextList().add(entry.getKey());
            matchList.add(entry.getKey());
            for (Date date : entry.getValue()) {
                if (null != wordItem.getValueMap().get(entry.getKey())) {
                    String valueInfo = wordItem.getValueMap().get(entry.getKey());
                    valueInfo += "~" + formatter.format(date);
                    wordItem.getValueMap().put(entry.getKey(), valueInfo);
                } else {
                    wordItem.getValueMap().put(entry.getKey(), formatter.format(date));
                }
            }
        }

        if (dateList.size() == 1) {
            wordItem.getValueList().add(formatter.format(dateList.get(0)));
            wordItem.getValueList().add(formatter.format(dateList.get(0)));
        } else {
            if (!wordSyntaxVo.getOriginalText().contains("同比") && !wordSyntaxVo.getOriginalText().contains("环比")) {
                wordItem.getValueList().add(formatter.format(dateList.get(0)));
                wordItem.getValueList().add(formatter.format(dateList.get(dateList.size() - 1)));
            } else {
                for (Date date : dateList) {
                    wordItem.getValueList().add(formatter.format(date));
                }
            }
        }

        String matchText = String.join(" ", matchList);
        wordItem.setMatchText(matchText);
        wordItem.setStandText(matchText);
        wordItem.setMatchType("date");
        wordItem.setSqlType("where");
        wordItem.setValueType("rangeBetween");
        wordSyntaxVo.getWordItemList().add(wordItem);
        nextNoFlag = true;
        return wordSyntaxVo;
    }

    // 自然日期识别 2023年
    public WordSyntaxVo natureV2DateSyntax(WordSyntaxVo wordSyntaxVo) {
        // 使用日期分词，将识别到的分词也作为字典
        if (nextNoFlag) {
            return wordSyntaxVo;
        }
        List<TimeNLP> timeNLPList = TimeNLPUtil.parse(wordSyntaxVo.getOriginalText());
        if (timeNLPList == null || timeNLPList.isEmpty()) {
            return wordSyntaxVo;
        }
        String matchText = "";
        WordSyntaxVo.WordItem wordItem = new WordSyntaxVo.WordItem();
        int firstLeng = 0;
        int secondLeng = 0;
        wordItem.setOrderNum(wordSyntaxVo.getOriginalText().indexOf(timeNLPList.get(0).getTimeExpression()));
        if (timeNLPList.size() == 1) {
            firstLeng = timeNLPList.get(0).getTimeNorm().length();
            secondLeng = firstLeng;
            matchText = timeNLPList.get(0).getTimeExpression();
            wordSyntaxVo.getMatchTextList().add(timeNLPList.get(0).getTimeExpression());
        } else if (timeNLPList.size() >= 2) {
            firstLeng = timeNLPList.get(0).getTimeNorm().length();
            secondLeng = timeNLPList.get(1).getTimeNorm().length();

            int start = wordSyntaxVo.getOriginalText().indexOf(timeNLPList.get(0).getTimeExpression());
            int end = wordSyntaxVo.getOriginalText().indexOf(timeNLPList.get(1).getTimeExpression()) + timeNLPList.get(1).getTimeExpression().length();

            matchText = wordSyntaxVo.getOriginalText().substring(start, end);
            wordSyntaxVo.getMatchTextList().add(timeNLPList.get(0).getTimeExpression());
            wordSyntaxVo.getMatchTextList().add(timeNLPList.get(1).getTimeExpression());


        }

        int formatDateLeng = Math.max(firstLeng, secondLeng);

        SimpleDateFormat formatter = null;
        if (formatDateLeng <= 5) {
            // 年
            formatter = new SimpleDateFormat("yyyy");
            wordItem.setWordType("year");
        } else if (formatDateLeng >= 7 && formatDateLeng <= 8) {
            // 月
            formatter = new SimpleDateFormat("yyyyMM");
            wordItem.setWordType("month");
        } else {
            // 日
            formatter = new SimpleDateFormat("yyyy-MM-dd");
            wordItem.setWordType("day");
        }
        //


        wordItem.setMatchText(matchText);
        wordItem.setStandText(matchText);


        if (matchText.contains("和") || matchText.contains("与")) {
            wordItem.setValueType("rangeIn");
        } else if (matchText.contains("到") || matchText.contains("至")) {
            wordItem.setValueType("rangeBetween");
        } else {
            wordItem.setValueType("rangeBetween");
        }
        wordItem.setMatchType("date");
        wordItem.setSqlType("where");
        if (timeNLPList.size() == 1) {
            wordItem.getValueList().add(formatter.format(timeNLPList.get(0).getTime()));
            wordItem.getValueList().add(formatter.format(timeNLPList.get(0).getTime()));
        } else {
            wordItem.getValueMap().put(timeNLPList.get(0).getTimeExpression(), formatter.format(timeNLPList.get(0).getTime()));
            wordItem.getValueMap().put(timeNLPList.get(1).getTimeExpression(), formatter.format(timeNLPList.get(1).getTime()));
            if (timeNLPList.get(0).getTime().after(timeNLPList.get(1).getTime())) {
                wordItem.getValueList().add(formatter.format(timeNLPList.get(1).getTime()));
                wordItem.getValueList().add(formatter.format(timeNLPList.get(0).getTime()));
            } else {
                wordItem.getValueList().add(formatter.format(timeNLPList.get(0).getTime()));
                wordItem.getValueList().add(formatter.format(timeNLPList.get(1).getTime()));
            }
        }
        nextNoFlag = true;
        wordSyntaxVo.getWordItemList().add(wordItem);

        return wordSyntaxVo;

    }


    public WordSyntaxVo lateDateSyntax(WordSyntaxVo wordSyntaxVo) {
        if (nextNoFlag) {
            return wordSyntaxVo;
        }

        wordSyntaxVo.setOriginalText(wordSyntaxVo.getOriginalText());

        String currentDatePatter = "(近|最近|最近的|最近地)(半|[" + CommonConstants.LARGE_NUMERALS + "]+|\\d+|[零壹贰叁肆伍陆柒捌玖]+)(天|日|周|年|月|个月|月份)(\\S+)";

        Pattern pattern = Pattern.compile(currentDatePatter);
        Matcher matcher = pattern.matcher(wordSyntaxVo.getOriginalText());
        Integer startIndex = null;
        if (matcher.find() && matcher.group().length() >= 4 && (matcher.group(1).contains("最近") || matcher.group(1).contains("近"))) {
            log.info("match date is {} ", matcher.group(1) + matcher.group(2) + matcher.group(3) + matcher.group(4));
            startIndex = matcher.start();
            // 获取当前日期
            LocalDate now = LocalDate.now();
            LocalDate calculatedDate = null;
            DateTimeFormatter formatter = null;
            String wordType = null;
            // 定义日期格式
            // 计算指定数量之前的日期范围
            switch (matcher.group(2)) {
                case "半":
                    switch (matcher.group(3)) {
                        case "个月":
                        case "月份":
                        case "月":
                            calculatedDate = now.minusDays(14);
                            formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd");
                            wordType = "day";
                            break;
                        case "年":
                            calculatedDate = now.minusMonths(5);
                            formatter = DateTimeFormatter.ofPattern("yyyyMM");
                            wordType = "month";
                            break;
                    }

                    break;
                default:

                    Integer dateBeforeNum = StringUtil.getDateBeforeNumber(matcher.group(2));
                    if (dateBeforeNum == null) {
                        break;
                    }
                    switch (matcher.group(3)) {
                        case "日":
                        case "天":
                            calculatedDate = now.minusDays(dateBeforeNum);
                            formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd");
                            wordType = "day";
                            break;
                        case "周":
                            calculatedDate = now.minusWeeks(dateBeforeNum);
                            break;
                        case "个月":
                        case "月份":
                        case "月":
                            calculatedDate = now.minusMonths(dateBeforeNum - 1);
                            formatter = DateTimeFormatter.ofPattern("yyyyMM");
                            wordType = "month";
                            break;
                        case "年":
                            calculatedDate = now.minusYears(dateBeforeNum - 1);
                            formatter = DateTimeFormatter.ofPattern("yyyy");
                            wordType = "year";
                            break;
                    }
            }

            if (calculatedDate != null && formatter != null) {

                String formattedCurrentDate = now.format(formatter);
                String formattedCalculatedDate = calculatedDate.format(formatter);

                WordSyntaxVo.WordItem wordItem = new WordSyntaxVo.WordItem();
                String matchText = matcher.group(1) + matcher.group(2) + matcher.group(3);
                wordItem.setMatchText(matchText);
                wordItem.setStandText(matchText);
                wordSyntaxVo.getMatchTextList().add(matchText);
                wordItem.setWordType(wordType);
                wordItem.setMatchType("date");
                wordItem.setMatchMethod("reg");
                wordItem.setOrderNum(startIndex);
                wordItem.setValueType("rangeBetween");
                wordItem.setSqlType("where");
                wordItem.getValueList().add(formattedCalculatedDate);
                wordItem.getValueList().add(formattedCurrentDate);
                wordSyntaxVo.getWordItemList().add(wordItem);
                this.nextNoFlag = true;
            }

        }
        return wordSyntaxVo;
    }


    public WordSyntaxVo quarterSyntax(WordSyntaxVo wordSyntaxVo) {
        wordSyntaxVo.setOriginalText(wordSyntaxVo.getOriginalText());
        String matchPattenText = wordSyntaxVo.getOriginalText();

        List<DimAllValuesInfo> dimAllValuesInfoList = dimAllValuesMapper.selectInfoByDimCode("DIM_e2c424a6997343a19fcaf6371883436d");
        for (DimAllValuesInfo dimAllValuesInfo : dimAllValuesInfoList) {
            if (matchPattenText.contains(dimAllValuesInfo.getValueFormatText())) {
                matchPattenText = matchPattenText.replace(dimAllValuesInfo.getValueFormatText(), "");
            }
        }
//        String currentDatePatter = "(前年|去年|今年|\\d+|\\d+年)([Qq])([" + CommonConstants.LARGE_NUMERALS + "]+|\\d+|[零壹贰叁肆伍陆柒捌玖]+)(\\S+)";
        String currentDatePatter = "(上1年|上一年|前一年|前年|去年|今年|\\d+|\\d+年)(Q1|Q2|Q3|Q4|q1|q2|q3|q4|第一季度|第二季度|第三季度|第四季度|一季度|二季度|三季度|四季度|第1季度|第2季度|第3季度|第4季度|1季度|2季度|3季度|4季度)";
        Pattern pattern = Pattern.compile(currentDatePatter);
        Matcher matcher = pattern.matcher(matchPattenText);
        Integer startIndex = null;
        List<String> valueList = new ArrayList<String>();
        WordSyntaxVo.WordItem wordItem = new WordSyntaxVo.WordItem();
        while (matcher.find()) {
            // && matcher.group().length() >= 3
            log.info("match quarter date is {} ", matcher.group(1) + matcher.group(2));
            startIndex = matcher.start();
            // 获取当前日期
            String wordType = "quarter";
            // 定义日期格式
            // 计算指定数量之前的日期范围
            // 22年
            String startYear = matcher.group(1);
            LocalDate now = LocalDate.now();
            Integer curYear = now.getYear();
            if (startYear.contains("年")) {
                startYear = startYear.substring(0, startYear.indexOf("年"));
                if (startYear.length() == 2) {
                    switch (startYear) {
                        case "上1":
                        case "前1":
                        case "上一":
                        case "前一":
                            startYear = String.valueOf(curYear - 1);
                            break;
                        default:
                            startYear = "20" + startYear;
                    }

                } else {

                    switch (startYear) {
                        case "前":
                            startYear = String.valueOf(curYear - 2);
                            break;
                        case "去":
                            startYear = String.valueOf(curYear - 1);
                            break;
                        case "今":
                            startYear = String.valueOf(curYear);
                            break;
                    }
                }
            } else {
                if (startYear.length() == 2) {
                    startYear = "20" + startYear;
                }
            }
            String quarterV = "";
            String quarterSubV = "";
            switch (matcher.group(2)) {
                case "Q1":
                case "q1":
                case "第一季度":
                case "一季度":
                case "第1季度":
                case "1季度":
                    quarterV = startYear + "1";
                    break;
                case "Q2":
                case "q2":
                case "第二季度":
                case "二季度":
                case "第2季度":
                case "2季度":
                    quarterV = startYear + "2";
                    break;
                case "Q3":
                case "q3":
                case "第三季度":
                case "三季度":
                case "第3季度":
                case "3季度":
                    quarterV = startYear + "3";
                    break;
                case "Q4":
                case "q4":
                case "第四季度":
                case "四季度":
                case "第4季度":
                case "4季度":
                    quarterV = startYear + "4";
                    break;
            }

            if (!quarterV.isEmpty()) {
                String matchText = matcher.group(1) + matcher.group(2);
                wordItem.setMatchText(matchText);
                wordItem.setStandText(matchText);
                wordSyntaxVo.getMatchTextList().add(matchText);
                wordItem.setWordType(wordType);
                wordItem.setMatchType("date");
                wordItem.setMatchMethod("reg");
                wordItem.setOrderNum(startIndex);
                wordItem.setValueType("rangeIn");
                wordItem.setSqlType("where");
                wordItem.getValueList().add(quarterV);
                wordItem.getValueMap().put(matchText, quarterV);
                this.nextNoFlag = true;
            }

        }
        if (!wordItem.getValueList().isEmpty()) {
            if (wordItem.getValueList().size() == 1) {
                wordItem.getValueList().add(wordItem.getValueList().get(0));
            }
            wordSyntaxVo.getWordItemList().add(wordItem);
        }

        return wordSyntaxVo;
    }

    public WordSyntaxVo curDateSyntax(WordSyntaxVo wordSyntaxVo) {
        // 使用日期分词，将识别到的分词也作为字典
        if (nextNoFlag) {
            return wordSyntaxVo;
        }
        String matchPattenText = wordSyntaxVo.getOriginalText();
        List<DimAllValuesInfo> dimAllValuesInfoList = dimAllValuesMapper.selectInfoByDimCode("DIM_e2c424a6997343a19fcaf6371883436d");
        for (DimAllValuesInfo dimAllValuesInfo : dimAllValuesInfoList) {
            if (matchPattenText.contains(dimAllValuesInfo.getValueFormatText())) {
                matchPattenText = matchPattenText.replace(dimAllValuesInfo.getValueFormatText(), "");
            }
        }
        wordSyntaxVo.setOriginalText(wordSyntaxVo.getOriginalText());
        String currentDatePatter = "(当年度|本年度|当年的|当日的|当天的|当月的|本年的|本月的|本季度|当季度|当年|本年|当月|本月|当天|当日|本季|当季|Q1|Q2|Q3|Q4|q1|q2|q3|q4)";
        Pattern pattern = Pattern.compile(currentDatePatter);
        Matcher matcher = pattern.matcher(matchPattenText);
        String formattedCurrentDate = "";
        if (matcher.find()) {
            LocalDate now = LocalDate.now();
            DateTimeFormatter formatter = null;
            Integer startIndex = matcher.start();
            WordSyntaxVo.WordItem wordItem = new WordSyntaxVo.WordItem();
            String wordType = "";
            // 获取当前日期
            if (matcher.group().contains("年")) {
                wordType = "year";
                formatter = DateTimeFormatter.ofPattern("yyyy");
                formattedCurrentDate = now.format(formatter);
            } else if (matcher.group().contains("月")) {
                wordType = "month";
                formatter = DateTimeFormatter.ofPattern("yyyyMM");
                formattedCurrentDate = now.format(formatter);
            } else if (matcher.group().contains("季")) {
                wordType = "quarter";
                Integer curYear = now.getYear();
                Integer curMonth = now.getMonthValue();
                Integer qu = curMonth / 4 + 1;
                formattedCurrentDate = curYear + "" + qu;
            } else if (matcher.group().contains("日") || matcher.group().contains("天")) {
                wordType = "day";
                formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd");
                formattedCurrentDate = now.format(formatter);
            } else if (matcher.group().toLowerCase().contains("q")) {
                wordType = "quarter";
                Integer curYear = now.getYear();
                String lastChar = matcher.group().substring(matcher.group().length() - 1);
                formattedCurrentDate = curYear + lastChar;
            }
            if (!formattedCurrentDate.isEmpty()) {
                wordItem.setMatchText(matcher.group());
                wordItem.setStandText(matcher.group());
                wordSyntaxVo.getMatchTextList().add(matcher.group());
                wordItem.setWordType(wordType);
                wordItem.setMatchType("date");
                wordItem.setMatchMethod("reg");
                wordItem.setOrderNum(startIndex);
                wordItem.setValueType("rangeBetween");
                wordItem.setSqlType("where");
                wordItem.getValueList().add(formattedCurrentDate);
                wordItem.getValueList().add(formattedCurrentDate);
                wordSyntaxVo.getWordItemList().add(wordItem);
                nextNoFlag = true;
            }

        }
        return wordSyntaxVo;
    }


    @Autowired
    private RestTemplate restTemplate;

    public DateExtractVo getDateExtractVo(String wordText) {
        HttpHeaders headers = new HttpHeaders();
        headers.add("Content-Type", "application/json");
        String url = "https://da-nlp-dev.inner.chj.cloud/hanlp/extract/time?text=" + wordText;

        try {
            DateExtractVo dateExtractVo = restTemplate.getForObject(url, DateExtractVo.class);

            if (dateExtractVo == null || !dateExtractVo.isSuccess()) {
                return null;
            }


            for (DateExtractVo.DateInfo dateInfo : dateExtractVo.getData()) {
                List<String> dateList = castList(dateInfo.getDetail().getTime(), String.class);
                if (null != dateList) {
                    SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd");
                    for (String dateK : dateList) {
                        Date date = format.parse(dateK);
                        dateInfo.getDateTime().add(date);
                    }
                }
            }

            return dateExtractVo;
        } catch (Exception e) {
            log.info("DateExtractVo error is {}", e.getMessage(), e);
            return null;
        }

    }

    public static <T> List<T> castList(Object obj, Class<T> clazz) {
        List<T> result = new ArrayList<T>();
        if (obj instanceof List<?>) {
            for (Object o : (List<?>) obj) {
                result.add(clazz.cast(o));
            }
            return result;
        }
        return null;
    }

}
