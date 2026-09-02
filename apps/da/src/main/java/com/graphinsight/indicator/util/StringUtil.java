package com.graphinsight.indicator.util;


import com.ibm.icu.text.RuleBasedNumberFormat;
import com.graphinsight.indicator.constant.IndicatorConstant;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.text.ParseException;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class StringUtil {

    private static final Pattern pattern = Pattern.compile("-?[0-9]+(\\.[0-9]+)?");

    public static boolean isCellNumber(String value) {

        //sql中的千分位逗号过滤
        value = value.replaceAll(",", "");
        return isNumber(value);

    }

    public static boolean isNumber(String value) {
        value = value.replaceAll(",", "");
        Matcher matcher = pattern.matcher(value);
        return matcher.matches();

    }

    /**
     * 生成BI平台的指标维度
     * @return
     */
    public static String getDimCode() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 10);
    }
    public static String getMeasCode() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 10);
    }

    public static String buildTaskId() {
        return "task" + System.currentTimeMillis() + UUID.randomUUID().toString().replace("-", "").substring(0, 5);
    }

    public static boolean isNotEmpty(String value) {
        return !isEmpty(value);
    }

    public static boolean isEmpty(String value) {

        boolean isNull = false;
        if ("null".equalsIgnoreCase(value) || value == null || value.isEmpty() || IndicatorConstant.BI_NULL.equalsIgnoreCase(value) || "-".equalsIgnoreCase(value)) {
            isNull = true;
        }
        return isNull;

    }

    public static boolean isAllBlank(List<String> values) {
        boolean ret = true;
        for(String v : values) {
            if (v != null && !v.isEmpty()) {
                ret = false;
                break;
            }
        }
        return ret;
    }

    public static String join(Collection var0, String var1) {
        StringBuffer var2 = new StringBuffer();

        for(Iterator var3 = var0.iterator(); var3.hasNext(); var2.append((String)var3.next())) {
            if (var2.length() != 0) {
                var2.append(var1);
            }
        }

        return var2.toString();
    }

    private static String bytesToHex(byte[] bytes) {
        StringBuffer sb = new StringBuffer();
        int dig = 0;
        for (byte b : bytes) {
            dig = b;
            if (dig < 0) {
                dig += 256;
            }
            if (dig < 16) {
                sb.append("0");
            }
            sb.append(Integer.toHexString(dig));
        }
        return sb.toString().toLowerCase();
    }

    public static String encrypt(String string) {
        String md5 = "";
        try {
            // 初始化MD5对象
            MessageDigest instance = MessageDigest.getInstance("MD5");
            // 将字符串变成byte数组
            byte[] bs = string.getBytes();
            // 得到128位字节数组
            byte[] digest = instance.digest(bs);
            // 转换成16进制
            md5 = bytesToHex(digest);
        } catch (NoSuchAlgorithmException e) {
            e.printStackTrace();
        }
        return md5;
    }


    public static UUID generateUUIDFromString(String name) {
        // 将字符串转换为字节数组
        byte[] nameBytes = name.getBytes(StandardCharsets.UTF_8);
        // 使用基于MD5哈希的方法生成UUID
        return UUID.nameUUIDFromBytes(nameBytes);
    }

    public static Integer getDateBeforeNumber(String numberStr) {

        RuleBasedNumberFormat rbnf = new RuleBasedNumberFormat(Locale.CHINA, RuleBasedNumberFormat.SPELLOUT);
        try {
            Number number = rbnf.parse(numberStr);
            return number.intValue();
        } catch (ParseException e) {
            //e.printStackTrace();
            return null;
        }
    }
}
