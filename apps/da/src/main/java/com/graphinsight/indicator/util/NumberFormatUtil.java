package com.graphinsight.indicator.util;

import lombok.extern.slf4j.Slf4j;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.text.NumberFormat;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * Date: 2022/6/22
 * Desc:
 */
@Slf4j
public class NumberFormatUtil {

    private static final String PARTTERN = "^(\\-|\\+)?\\d+(\\.\\d+)?$";

    /**
     * 把数值型的字符串转换为BigDecimal
     * 支持百分比、千分位
     * @param param
     * @return
     */
    public static BigDecimal formatExceptionWithZero(String param) {
        if (Objects.isNull(param)) {
            return BigDecimal.ZERO;
        }
        try {
            String value = param;
            // 去掉千分位
            value = value.replaceAll(",", "");
            if (value.contains("%")) {
                // 去掉百分号
                value = value.replaceAll("%", "");
                Double vdd = Double.parseDouble(value) / 100;
                return BigDecimal.valueOf(vdd);
            } else {
                double doubleValue = new DecimalFormat().parse(value).doubleValue();
                return BigDecimal.valueOf(doubleValue);
            }

        } catch (Exception e) {
            log.error("数字转换异常-formatExceptionWithZero,str: {}",param);
            return BigDecimal.ZERO;
            // throw IndicatorParamNotValidException.error("数字转换异常,str: " + param);
        }
    }


    /**
     * 把数值型的字符串转换为BigDecimal
     * 支持百分比、千分位
     * @param param
     * @return
     */
    public static BigDecimal format(String param) {
        if (Objects.isNull(param)) {
            return null;
        }
        try {
            String value = param;
            // 去掉千分位
            value = value.replaceAll(",", "");
            if (value.contains("%")) {
                // 去掉百分号
                value = value.replaceAll("%", "");
                Double vdd = Double.parseDouble(value) / 100;
                return BigDecimal.valueOf(vdd);
            } else {
                double doubleValue = new DecimalFormat().parse(value).doubleValue();
                return BigDecimal.valueOf(doubleValue);
            }

        } catch (Exception e) {
            log.error("数字转换异常,str: {}",param);
            return null;
            // throw IndicatorParamNotValidException.error("数字转换异常,str: " + param);
        }
    }

    public static boolean isNumbericWithComma(String str){
        if (! StringUtils.hasLength(str)){
            return false;
        }
        str = str.replaceAll(",","");
        str = str.replaceAll("%","");
        return Pattern.matches(PARTTERN,str);
    }

    public static boolean isNumberic(String str){
        if (! StringUtils.hasLength(str)){
            return false;
        }
        return Pattern.matches(PARTTERN,str);
    }

    public static String toPercent(BigDecimal bigDecimal){
        NumberFormat percent = NumberFormat.getPercentInstance();  //建立百分比格式化引用
        percent.setMinimumFractionDigits(2);
        return percent.format(bigDecimal);
    }

    //将bigDecimal转为千分位字符串
    public static String format(BigDecimal bigDecimal){
        DecimalFormat df1 = new DecimalFormat("###,###.00");
        df1.setMinimumIntegerDigits(1);
        return df1.format(bigDecimal);
    }
}
