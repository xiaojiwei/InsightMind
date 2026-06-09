package com.graphinsight.indicator.util;

import java.util.regex.Pattern;

/**
 * SQL 注入检测工具，替代公司私有 DevSecOps SDK
 */
public class SqlInjectionUtils {

    private static final Pattern SQL_INJECTION_PATTERN = Pattern.compile(
            "(?i).*('|--|;|/\\*|\\*/|xp_|union\\s|select\\s|insert\\s|update\\s|delete\\s|drop\\s|truncate\\s|exec\\s|execute\\s|cast\\(|convert\\().*"
    );

    /**
     * 检测输入是否包含 SQL 注入风险
     * @return true 表示存在风险
     */
    public static boolean check(String input) {
        if (input == null || input.isEmpty()) {
            return false;
        }
        return SQL_INJECTION_PATTERN.matcher(input).matches();
    }
}
